import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, Inject, EventEmitter } from '@angular/core';
import { Observable, Subscription, of } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';
import { Router } from '@angular/router';
// services
import { ChatsDataService } from './chats-data.service';
import { LoadBalancerService } from './load-balancer.service';
import { ResponseNotifierService } from './response-notifier.service';
import { UserSettingsService } from './user-settings.service';
import { UserService } from './user.service';
import { SessionService } from './session.service';
// models
import { Invitation } from '../models/Invitation';
import { Message } from '../models/Message';
import { User } from '../models/User';
import { Settings } from '../models/Settings';
import { Writing } from '../models/Writing'; 
import { ChatData } from '../models/ChatData';
import { Chat } from '../models/Chat';
import { Configuration } from '../models/Configuration';
import { ChatOffsetUpdate } from '../models/ChatOffsetUpdate';
import { PartitionOffset } from '../models/PartitionOffset';
import { UserOffsetUpdate } from '../models/UserOffsetUpdate';




@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  
  private wsConnection:          WebSocket | undefined
  private wsPingSender:     NodeJS.Timeout | undefined
  private reconnectWSTimer: NodeJS.Timeout | undefined
  private writerCleaner:    NodeJS.Timeout | undefined
  private userObj:                    User | undefined
  
  private someoneIsWriting = false
  private reconnectWS      = true
  private initialized      = false
  // private myUserId: string | undefined

  // public newMessagesEmitter: EventEmitter<Array<Message>>      = new EventEmitter<Array<Message>>()
  // public oldMessagesEmitter: EventEmitter<Array<Message>>      = new EventEmitter<Array<Message>>()
  // public invitationEmitter:  EventEmitter<Invitation>          = new EventEmitter<Invitation>()
  // public writingEmitter:     EventEmitter<Writing | undefined> = new EventEmitter<Writing| undefined>()
  
  // to można usunąć bo to są eventy obsługiwane przez wewnętrzne  metody
  //public restartWSEmitter:   EventEmitter<boolean>             = new EventEmitter<boolean>()



  //public wsConnEmitter:      EventEmitter<boolean>             = new EventEmitter<boolean>()


  

  // co trzeba zasubskrybować 

  // subskrypcja wyłapująca, że jest potrzeba restartu websocketu
  private restartWSSubscription:          Subscription | undefined
  
  
  //  subskrypcja tego służyła do fetchowania (wysyłania prośby do servera) o stare wiadomości 
  // private wsConnectionSubscription:       Subscription | undefined


  // jak przyjdzie event, żeby wylogować to clearowanie wszystkich servisów przez disconnect? 
  // i przekierowanie na /sessiontimeout
  private logoutSubscription:             Subscription | undefined


  // to będzie wywoływane jak w chat-service nastąpi emisja żeby updejtować offset w backendzie
  private chatOffsetUpdateSubscription:   Subscription | undefined
  

  // subskrypcja do przechwytywania eventu o tym, że piszemy w chatcie będziemy w  nniej wysyłać przez WS info o writing
  private writingSubscription:            Subscription | undefined


  // subskrypcja wyłapująca event o wysłaniu nowej wiadomości
  // w tej subscrypcji wysyłamy nową wiadomość do backendu przez WS
  private sendMessageSubscription:        Subscription | undefined


  // subskrypcja odbierająca sygnał o potrzebie fetchowania starszych wiadomości
  // z backendu. wysyła przez ws zapytanie info z jakiego czatu nalezy pobrać stare wiadomości
  private fetchOlderMessagesSubscription: Subscription | undefined



  /*
  założenia:

  1. W zamyśle wszelkie zapytanie są wysyłane przez ten servis
  2. jak przychodzi jakaś odpowiedź z serwera to jest ona następnie wstrzykiwana do odpowiedniej metody odpowiedniego podserwisu
  3. jak podserwis przetworzy te dane to za pomocą emitera emituje jakiś event np aktualizuj czatlistę 
  4. componenty subskrybują te emitery tak, ze jak tylko pojawia się event to wczytują dane z danego servisu 
     i aktualizują komponent. 


  Do wykonania:
  1. przenieść wszystkie uaktualnienia sesji i restarty logout-service? z user-service do connection service     

  */

  constructor(private http: HttpClient, 
              // @Inject("API_URL") private api: string,
              private userService: UserService,
              private settingsService: UserSettingsService,
              private chatService: ChatsDataService,
              private responseNotifier: ResponseNotifierService,
              private loadBalancer: LoadBalancerService,
              private session: SessionService,
              private router: Router) { 

                console.log('ConnectionService constructor called.')
                // this.assignSubscriptions()
                const uid = this.session.getSavedUserId();
                if ( uid ) {
                  // poniższy update przeniosłem do subscribe po pobraniu danych o 
                  // użytkowniku, który jest zapisany w ciasteczku.
                  // this.updateSessionViaUserId( userId )


                  this.session.updateSession( uid ) // żeby nie został automatycznie wylogowany. 

                  console.log('Session is valid.') 

                  const s = this.user( uid )
                  if ( s ) {
                    s.subscribe({
                      next: (response) => {
                        const body = response.body
                        if ( body ){
                          console.log('ConnectionSerivice.constructor() fetching user login and settings' )
                          this.initialize( body.user, body.settings, body.chatList )
                          //this.setUserAndSettings(body.user, body.settings)
                          // this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
                          //this.restartLogoutTimer()
                          // this.chatsService.initialize( body.chatList, body.user )
                          this.connectViaWebsocket() 
                          this.updateSession()
                        }
                        else {
                          // print error message
                        }
                      },
                      error: (error) => {
                        console.log(error) 
                        this.disconnect() // zmienić na disconnect tak aby pokasował wszystkie dane
                        console.log('redirection to logging page')
                        this.router.navigate([''])
                      },
                      complete: () => {}
                    })
                  }




                } else this.router.navigate([''])
              }





  assignSubscriptions() {
    console.log('ConnectionService.assignSubscriptions() calling')
    
    if ( ! this.logoutSubscription ) {
      this.logoutSubscription = this.session.logoutEmitter.subscribe(
        (c) => {
          this.disconnect()
          // stare  czyszczę wszystko 
          //this.clearService()
          // i nawiguję na stronę session-timeout
          this.router.navigate(['session-timeout'])
          // this.router.navigate([''])
        }
      )
    }

    if ( ! this.chatOffsetUpdateSubscription ) {
      this.chatOffsetUpdateSubscription = this.chatService.chatOffsetUpdateEmitter.subscribe(
        (chatOffsetUpdate) => {
          this.sendChatOffsetUpdate( chatOffsetUpdate )
        }
      )
    }

    if ( ! this.writingSubscription ) {
      this.writingSubscription = this.chatService.sendingWritingEmitter.subscribe(
        (w) => {
          if ( w ) this.sendWriting( w )
        }
      )
    }

    if ( ! this.sendMessageSubscription ){
      this.sendMessageSubscription  = this.chatService.sendMessageEmitter.subscribe(
        (m) => {
          if (this.userObj ){
            const body = {
              user:    this.userObj,
              message: m
            }
            this.sendMessage( body )
          }
        }
      )
    }


    if ( ! this.fetchOlderMessagesSubscription ) {
      this.writingSubscription = this.chatService.fetchOlderMessagesEmitter.subscribe(
        ( chatId ) => {
          this.fetchOlderMessages( chatId )
        }
      )
    }


    // poniższe subskrypcje zastąpiłem bezpośrednim wywołaniem odpowiedniej metody

    /* if ( ! this.restartWSSubscription ) {
      this.restartWSSubscription = this.restartWSEmitter.subscribe(
        (r: boolean) => {
          // if we get true we need to start timer end trying to reconnect
          
        }
      )
    } */

    /* if ( ! this.wsConnectionSubscription ) {
      // here we simply notify that all needed data are loaded
      // and WS connection is established 
      // so all fetching sobscribers can load data. 
      this.wsConnectionSubscription = this.wsConnEmitter.subscribe(
        ( bool ) => { 
          // this.dataFetched( 1 ) 
        }
      )
    } */

  } 









  connectViaWebsocket() {
    if (this.userObj) {
      const conf: Configuration = {
        me: this.userObj,
        joiningOffset: this.settingsService.settings.joiningOffset,
        chats: this.chatService.chatAndUsers.map(
          (c , i, arr) => {
            return {
              chatId: c.chat.chatId,
              partitionOffset: c.partitionOffsets.map(
                (parOff, i, arr2) => {
                  return {
                    partition: parOff.partition,
                    offset: parOff.offset
                  }
                }
              )
            }
          }
        )
      }
      this.connectViaWS( conf );
    } 
  }








  initialize(user: User, setttings: Settings, chatList: ChatData[]) {
    this.userObj = user
    // this.userService.initialize( user )
    this.settingsService.initialize(user, setttings)
    this.chatService.initialize( chatList, user)
    this.initialized = true
    this.assignSubscriptions()
    this.connectViaWebsocket()
  }




  isInitlized(): boolean {
    return this.initialized
  }



  // rest api calls


  signUp(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined{
    const fakeUserId = uuidv4();
    this.session.setNewSession(fakeUserId); 
    const body = {
      login: login,
      pass: pass,
      userId: ''
    };
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server) {
      return this.http.post<{user: User, settings: Settings}>(server.getURIwithPath('/signup'), body, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined
  }  


// 

  signIn(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings, chatList: Array<ChatData>}>> | undefined {
    const fakeUserId = uuidv4();
    this.session.setNewSession(fakeUserId); 
    const body = {
      login: login,
      pass: pass,
      userId: ''
    };
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.post<{user: User, settings: Settings, chatList: Array<ChatData>}>(server.getURIwithPath('/signin'), body, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined
  }




  logout(): Observable<HttpResponse<string>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.get<string>(server.getURIwithPath('/logout'),{
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  user(userId: string): Observable<HttpResponse<{user: User, settings: Settings, chatList: Array<ChatData>}>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token  && server) {
      // this.updateSession() nie możemy updejtować sesji do puki nie mamy informacji zwrotnej
      // dopiero jak w subskrybcji przyjdzie info to wtedy należy wywołać updejtowanie sesji albo w ogóle jej start
      return this.http.get<{user: User, settings: Settings, chatList: Array<ChatData>}>(server.getURIwithPath(`/user/${userId}`), {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    }
    else return undefined
  }




  updateJoiningOffset(userId: string, body: UserOffsetUpdate): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${userId}/updateJoiningOffset`),  body , { 
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    }
    else return undefined
  }



  
  changeSettings(s: Settings): Observable<HttpResponse<any>> | undefined  {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${this.userObj.userId}/changeSettings`), s, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  changeLogin(userId: string, newLogin: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${userId}/changeLogin`), newLogin, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }



  
  changePassword(userId: string, oldPassword: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      const body = {
        oldPass: oldPassword,
        newPass: newPassword
      }
      return this.http.put<any>(server.getURIwithPath(`/user/${userId}/changePassword`), body, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }




  searchUser(userId: string, search: string) : Observable<HttpResponse<User[]>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.get<User[]>(server.getURIwithPath(`/user/${userId}/searchUser`), { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json',
        params: new HttpParams().set('u', search)
      });
    } else return undefined;
  }



    
  newChat(me: User, chatName: string, users: string[]): Observable<HttpResponse<ChatData[]>> | undefined  {
    const token  = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      const body = {
        me: me,
        users: users,
        chatName: chatName
      }
      return this.http.post<ChatData[]>(server.getURIwithPath(`/user/${me.userId}/newChat`), body, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



  getChats(userId: string): Observable<HttpResponse<Array<ChatData>>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.get<Array<ChatData>>(server.getURIwithPath(`/user/${userId}/chats`), {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  getChatData(userId: string, chatId: string): Observable<HttpResponse<{chat: Chat, partitionOffsets: Array<{partition: number, offset: number}>}>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.get<{chat: Chat, partitionOffsets: Array<{partition: number, offset: number}>}>(server.getURIwithPath(`/user/${userId}/chats/${chatId}`), {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



  
  leaveChat(userId: string, chatId: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.delete<any>(server.getURIwithPath(`/user/${userId}/chats/${chatId}`), {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  getChatUsers(userId: string, chatId: string): Observable<HttpResponse<User[]>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.get<User[]>(server.getURIwithPath(`/user/${userId}/chats/${chatId}/users`), {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  setChatSettings(userId: string, chat: Chat): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${userId}/chats/${chat.chatId}/chatSettings`), chat, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }


  

  addUsersToChat(userId: string, login: string, chatId: string, chatName: string, userIds: string[], pOffsets: PartitionOffset[]): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      const body = {
        invitersLogin: login,
        chatName: chatName,
        users: userIds,
        partitionOffsets: pOffsets
      }
      return this.http.post<any>(server.getURIwithPath(`/user/${userId}/chats/${chatId}/addNewUsers`), body, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }










  /*
  WEBSOCKET
  */


  private connectViaWS(conf: Configuration) {
    const server = this.loadBalancer.currentServer
    if (this.wsConnection === undefined && server) {
      console.log(`Initializing Connection via WebSocket to: ws://${server.getURIwithoutProtocol()}`)
      this.wsConnection = new WebSocket(`ws://${server.getURIwithoutProtocol()}/user/${conf.me.userId}/ws`);
      // this.wsConnection = new WebSocket(`ws://localhost:9000/user/${conf.me.userId}/ws`);
      this.wsConnection.onopen = () => {
        console.log('WebSocket connection opened.');
        this.wsConnection?.send( JSON.stringify( conf ) )

        //w konfiguracji należy przesłać również informacje o sesji ???
        //tak aby server był w stanie sprawdzić czy user ma ważną sesję

        if ( this.reconnectWS ) {
          this.responseNotifier.printNotification(
            {
              header: 'Information',
              // code: 0,
              message: 'Connection retrieved.'
            }
          )
        }

        this.reconnectWS = true
        this.restartWS( false )
        // this.restartWSEmitter.emit( false )

        //tutaj //  zdefiniować cleaner, który następnie będzie usuwany w metodzie 
        // cleaner musi wysyłać wiadomość tylko jak zmienna 
        // someoneIsWriting jest na true
        // emitter wysyła wtedy po 0.5 s undefined
        // jeśli jest na false to emitter nie wysyła nic 
        this.writerCleaner = setInterval(() => {
          if ( this.someoneIsWriting ) this.chatService.showWriting( undefined )
          this.someoneIsWriting = false
        }, 1200)

      };
      this.wsConnection.onmessage = (msg: any) => {
        const body = JSON.parse( msg.data )
        if ( body.conf ) {
          console.log('got config: ', body.conf )
        }          
        if ( body.newMsgList ) {
          console.log('got list of NEW message: ', body.newMsgList )
          this.chatService.insertNewMessages( body.newMsgList )
          // this.newMessagesEmitter.emit( body.newMsgList )
        }
        if ( body.oldMsgList ) {
          console.log('got list of OLD message: ', body.oldMsgList )
          this.chatService.insertOldMessages( body.oldMsgList )
          // this.oldMessagesEmitter.emit( body.oldMsgList )
        }
        if ( body.inv ) {
          console.log('got invitation: ', body.inv )
          this.handleInvitation( body.inv )
          // this.invitationEmitter.emit( body.inv )
        }
        if ( body.wrt ) {
          // console.log('got writing: ', body.wrt )
          if (body.wrt.writerId != this.userObj?.userId) {
            this.someoneIsWriting = true
            this.chatService.showWriting( body.wrt )
          }          
        }
        if (body.comm == 'opened correctly') {
          console.log('WS connection opend correctly.')
          if ( this.chatService.selectedChat ) {
            this.fetchOlderMessages( this.chatService.selectedChat )
          } else console.error('No chat selected. ')
          //this.wsConnEmitter.emit( this.isWSconnected() )
          this.startPingSender()
        }
        if ( body.num && body.message) {
          console.log('got ResponseBody()' + body.message )
          // if (body.num) this.reconnectWS = false
        }
        /* else {
          console.log('got other message: ', body)
        }   */        
      }
      this.wsConnection.onclose = () => {
        console.log('WebSocket connection closed.');
        if ( this.wsConnection ) this.wsConnection = undefined
        if ( this.wsPingSender ) {
          clearInterval( this.wsPingSender )
          this.wsPingSender = undefined
        }
        if ( this.reconnectWS ) {
          this.loadBalancer.rebalance()
          // toast, that we lost connection
          this.responseNotifier.printError(
            {
              header: 'Connection Error',
              code: 1,
              message: 'Connection lost, try in a few minutes.'
            }
          )
          this.restartWS( this.reconnectWS )
          // this.restartWSEmitter.emit( this.reconnectWS )
        } else console.error('reconnectWS is set to FALSE')
      };
      this.wsConnection.onerror = (error) => {
        console.error('error from WS connection', error)
        // here probably we should close connection ??? and restart it ???
      };
    }
  }


  private restartWS(b: boolean) {
    if ( b ) {
      if ( ! this.reconnectWSTimer ) {
        console.log('initializing reconnectWSTimer. ')
        this.reconnectWSTimer = setInterval( () => {
          console.log('inside reconnectWSTimer trying to reconnect WS. ')
          this.connectViaWebsocket()
        }, 2000) // we try reconnect every 2s
      }          
    } else {
      // if we get false this means that we need to stop timer,
      // because we connected, 
      // or we disconnected and we do not need to reconnect
      if ( this.reconnectWSTimer ) {
        clearInterval( this.reconnectWSTimer )
        this.reconnectWSTimer = undefined
      }
    }
  }




  // methods sending different data via WS to backend


  private sendPoisonPill() {
    if (this.wsConnection) {
      console.log('sending PoisonPill to server.');
      this.wsConnection.send('PoisonPill');
    } else {
      console.error('WS connection is closed now. ');
    }
  }




  private sendMessage(body: {user: User, message: Message}) {
    if (this.wsConnection) {
      console.log('sending my message to server.');
      this.wsConnection.send(JSON.stringify( body ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }


  
  /*
    DEPRECATED

    metoda nie jest używana bo, zaproszenia są wysyłane automatycznie przez backend 
    jak tylko czat zostanie poprawnie utworzony. 
  */
  private sendInvitation(inv: Invitation) {
    if (this.wsConnection) {
      console.log('sending invitation to server.');
      this.wsConnection.send(JSON.stringify( inv ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  } 




  handleInvitation(invitation: Invitation) {
    if ( this.userObj ) {
      const c = this.getChatData( this.userObj.userId , invitation.chatId )
      if ( c ) {
        const cSub = c.subscribe({
          next: (response) => {
            if (response.ok){
              const body = response.body
              if ( body ) {
                const cd: ChatData =  {
                  chat: body.chat,
                  partitionOffsets: invitation.partitionOffsets,
                  messages: new Array<Message>(),
                  unreadMessages: new Array<Message>(),
                  users: new Array<User>(),
                  isNew: true,
                  emitter: new EventEmitter<ChatData>()  
                }
                
                
                //  tutaj musimy zapewnić, że dodawanie nowego chatu spowoduje wywołanie emitera odświeżającego chat-listę
                this.chatService.addNewChat( cd )  // to automatycznie odświeża listę czatów
                this.startListeningFromNewChat( cd.chat.chatId, cd.partitionOffsets )
                



                // this.dataFetched( 2 ) 
                

                if ( this.userObj ) {
                  const bodyToSent: UserOffsetUpdate = {
                    userId: this.userObj.userId,
                    joiningOffset: invitation.myJoiningOffset                    
                  }

                  // updejtujemy joining offset tak aby otrzymywać tylko najnowsze zaproszenia do chatów
                  const u = this.updateJoiningOffset(this.userObj.userId,  bodyToSent )
                  if ( u ) {
                    const sub = u.subscribe({
                      next: (response) => {
                        if ( response.ok ) {
                          this.settingsService.settings.joiningOffset = invitation.myJoiningOffset
                          console.log('joining Offset updated ok. to ', invitation.myJoiningOffset)
                        }
                          
                      },
                      error: (err) => {
                        console.log('Error during joining offset update', err)
                      },
                      complete: () => {}
                    })
                  }
                }                  
              }                
            }              
          },
          error: (err) => {
            console.error('error in calling getChatData() in invitationSubscription', err) 
          },
          complete: () => {}
        })
      }
    }    
  }



  private sendWriting(w: Writing) {
    if (this.wsConnection) {
      console.log('sending writing to server.');
      this.wsConnection.send(JSON.stringify( w ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }




  private sendChatOffsetUpdate(u: ChatOffsetUpdate) {
    if (this.wsConnection) {
      console.log('sending offset update to server.');
      this.wsConnection.send(JSON.stringify( u ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }



  
  // updateSession(sendUpdateToServer: boolean) {
  updateSession() {
    if (this.wsConnection && this.userObj) {
      console.log('sending session update to server.');
      this.session.updateSession(this.userObj.userId)
      const ksid = this.session.getKsid()
      if ( ksid ) { //&& sendUpdateToServer
        const session = {
          sessionId: ksid.sessId,
          userId:    ksid.userId,
          validityTime: ksid.validityTime
        }
        this.wsConnection.send(JSON.stringify( session ));
      }
    } else {
      console.error('Did not send data, open a connection first');
    }  
  }





  
  startListeningFromNewChat(chatId: string, partOffsets: PartitionOffset[]) {
    if (this.wsConnection) {
      console.log('sending New chat data to server to start listentning.');
      const body = {
        chatId: chatId,
        partitionOffset: partOffsets
      }
      this.wsConnection.send(JSON.stringify( body ));
    } else {
      console.error('Did not send data, open a connection first');
    }  
  }




  private fetchOlderMessages(chatId: string) {
    if (this.wsConnection) {
      console.log('sending request to fetch older messages.');
      const body = { chatId: chatId }
      this.wsConnection.send(JSON.stringify( body )); 
    } else {
      console.error('Did not send data, open a connection first');
    } 
  }




  private startPingSender() {
    this.wsPingSender = setInterval(() => {
      if ( this.wsConnection )
        this.wsConnection.send('ping')
    }, 60000 ) // ping empty message every 1 minute
  }




  private closeWebSocket() {
    if (this.wsConnection) {
      this.reconnectWS = false
      console.log('sending PoisonPill to server.');
      this.wsConnection.send('PoisonPill')
      this.someoneIsWriting = false
      if ( this.writerCleaner ) { 
        clearTimeout( this.writerCleaner )
        this.writerCleaner = undefined
      }
      //this.sendPoisonPill()
      this.wsConnection.close()
      console.log('connection deeactivated.');
      this.wsConnection = undefined;
    }
  }




  /* getUserId(): string | undefined {
    return this.session.getSavedUserId();
  } */



  isWSconnected(): boolean {
    if ( this.wsConnection ) {
      const state =  this.wsConnection.readyState
      return state == this.wsConnection.OPEN
    } else return false 
  }




  isWSconnectionDefined(): boolean {
    return this.wsConnection !== undefined
  }



  getUser(): User | undefined {
    return this.userObj
  }




  /*
    KSID methods
  */


/* 
  isSessionValid(): boolean {
    return this.session.isSessionValid();
  }  



  // use sendSessionUpdate() insted

   updateSession(userId: string) {
    this.session.updateSession(userId); 
  } 



  setNewKSID(userId: string) {
    this.session.setNewSession(userId);
  }



  getSessionToken(): string | undefined {
    return this.session.getSessionToken();
  }



  invalidateSession() {
    this.session.invalidateSession();
  }




  setUserId(userId: string) {
    this.myUserId = userId
  }

 */










  // closing methods


  clearSubscriptions() {
    if ( this.logoutSubscription )             this.logoutSubscription.unsubscribe()
    if ( this.chatOffsetUpdateSubscription )   this.chatOffsetUpdateSubscription.unsubscribe()
    if ( this.writingSubscription )            this.writingSubscription.unsubscribe() 
    if ( this.sendMessageSubscription )        this.sendMessageSubscription.unsubscribe()
    if ( this.fetchOlderMessagesSubscription ) this.fetchOlderMessagesSubscription.unsubscribe() 
    if ( this.restartWSSubscription )          this.restartWSSubscription.unsubscribe()
    // if ( this.wsConnectionSubscription )       this.wsConnectionSubscription.unsubscribe()
  }





  closeAllSubServices() {
    this.chatService.clear()
    this.session.clearService()
    // this.settingsService.
    // this.responseNotifier
     // this.loadBalancer has no clear methosd
  }





  disconnect() {
    // nowe
    this.closeAllSubServices();
    
    //   nowe
    this.initialized = false
    this.userObj = undefined
    
    //   stare
    this.reconnectWS = false
    this.session.invalidateSession()
    if ( this.wsPingSender ) {
      clearInterval( this.wsPingSender )
      this.wsPingSender = undefined
    }
    this.sendPoisonPill()
    this.wsConnection?.close()
    this.wsConnection = undefined
    
    // nowe - zamykanie pozostałych servisów i zamykanie subscrybcji
    this.clearSubscriptions()
  }            




}

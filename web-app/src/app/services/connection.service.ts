import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, Inject, EventEmitter } from '@angular/core';
import { Observable, Subscription, of } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';
// import { Router } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
// services
import { ChatsDataService } from './chats-data.service';
import { LoadBalancerService } from './load-balancer.service';
import { ResponseNotifierService } from './response-notifier.service';
import { UserSettingsService } from './user-settings.service';
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
  private reconnectWS      = false
  private initialized      = false


  // called only externally, when we want fetch data. 
  dataFetchedEmitter: EventEmitter<number> = new EventEmitter<number>()

  // ten emitter będzie służył do notificacji, że servis został zainicjowany
  // wtedy podległe componenty mogą zaciągać dane poprzez uruchamianie innych emiterrów. 
  serviceInitializedEmitter: EventEmitter<number> = new EventEmitter<number>()

  
  // jak przyjdzie event, żeby wylogować to clearowanie wszystkich servisów przez disconnect? 
  // i przekierowanie na /sessiontimeout
  private logoutSubscription:             Subscription | undefined


  // to będzie wywoływane jak w chat-service nastąpi emisja żeby updejtować offset w backendzie
  private chatOffsetUpdateSubscription:   Subscription | undefined
  

  // subskrypcja odbierająca sygnał o potrzebie fetchowania starszych wiadomości
  // z backendu. wysyła przez ws zapytanie info z jakiego czatu nalezy pobrać stare wiadomości
  private fetchOlderMessagesSubscription: Subscription | undefined


  

  constructor(private http: HttpClient, 
              // @Inject("API_URL") private api: string,
              private settingsService: UserSettingsService,
              private chatService: ChatsDataService,
              private responseNotifier: ResponseNotifierService,
              private loadBalancer: LoadBalancerService,
              private session: SessionService,
              private router: Router) { 

                console.log('ConnectionService.constructor()')
                // this.assignSubscriptions()
                const uid = this.session.getSavedUserId();
                if ( uid ) {
                  // poniższy update przeniosłem do subscribe po pobraniu danych o 
                  // użytkowniku, który jest zapisany w ciasteczku.
                  // this.updateSessionViaUserId( userId )
                  console.log('ConnectionService.constructor() -> session.getSavedUserId() -> user session is VALID')
                  // to avoid logout 
                  this.session.updateSession( uid ) 
                  // downloading user data ( chats, settings and user info)
                  const s = this.user( uid )
                  if ( s ) {
                    s.subscribe({
                      next: (response) => {
                        const body = response.body
                        if ( body ){
                          console.log('ConnectionSerivice.constructor() -> user().subscribe() ->  fetching user login and settings' )
                          this.initialize( body.user, body.settings, body.chatList )
                          this.session.restartLogoutTimer()
                          if ( ! this.wsConnection ) this.connectViaWebsocket() 
                        }
                        else {
                          console.error('ConnectionSerivice.constructor() -> user().subscribe() -> empty body ???')
                        }
                      },
                      error: (error) => {
                        console.error('ConnectionSerivice.constructor() -> user().subscribe() -> cannot download user data -> Redirecting to logging page', error)
                        // clear out service 
                        this.disconnect() 
                        console.error('ConnectionSerivice.constructor() -> user().subscribe() -> Redirecting to logging page')
                        this.router.navigate([''])
                      },
                      complete: () => {}
                    })
                  }
                } 
                // if there is no user cookie we redirect to logging page
                else this.router.navigate([''])
              }





  assignSubscriptions() {
    console.log('ConnectionService.assignSubscriptions()')

    if ( ! this.logoutSubscription ) {
      this.logoutSubscription = this.session.logoutEmitter.subscribe(
        (c) => {
          console.log('ConnectionService.logoutSubscription called from sessionService via logoutEmitter')
          this.disconnect()
          this.router.navigate([''])
        }
      )
    }


    if ( ! this.chatOffsetUpdateSubscription ) {
      this.chatOffsetUpdateSubscription = this.chatService.chatOffsetUpdateEmitter.subscribe(
        (chatOffsetUpdate) => {
          console.log('ConnectionService.chatOffsetUpdateSubscription')
          this.sendChatOffsetUpdate( chatOffsetUpdate )
        }
      )
    }


    if ( ! this.fetchOlderMessagesSubscription ) {
      this.fetchOlderMessagesSubscription = this.chatService.fetchOlderMessagesEmitter.subscribe(
        ( chatId ) => {
          console.log('ConnectionService.fetchOlderMessagesSubscription')
          this.fetchOlderMessages( chatId )
        }
      )
    }
  } 





  connectViaWebsocket() {
    if (this.userObj) {
      console.log('ConnectionService.connectViaWebsocket()')
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
    console.log('ConnectionService.initialize()')
    this.userObj = user
    this.assignSubscriptions()
    this.settingsService.initialize(user, setttings)
    this.chatService.initialize( chatList, user)
    this.initialized = true
    if ( ! this.wsConnection ) this.connectViaWebsocket()
  }




  isInitlized(): boolean {
    console.log('ConnectionService.isInitlized()')
    return this.initialized
  }



  // rest api calls


  signUp(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    console.log('ConnectionService.signUp()')
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
    console.log('ConnectionService.signIn()')
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
    console.log('ConnectionService.logout()')
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
    console.log('ConnectionService.user()')
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
    console.log('ConnectionService.updateJoiningOffset()')
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
    console.log('ConnectionService.changeSettings()')
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




  changeLogin( newLogin: string): Observable<HttpResponse<any>> | undefined {
    console.log('ConnectionService.changeLogin()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${this.userObj.userId}/changeLogin`), newLogin, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }



  
  changePassword(oldPassword: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    console.log('ConnectionService.changePassowrd()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      const body = {
        oldPass: oldPassword,
        newPass: newPassword
      }
      return this.http.put<any>(server.getURIwithPath(`/user/${this.userObj.userId}/changePassword`), body, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }




  searchUser( search: string) : Observable<HttpResponse<User[]>> | undefined {
    console.log('ConnectionService.searchUser()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      return this.http.get<User[]>(server.getURIwithPath(`/user/${this.userObj.userId}/searchUser`), { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json',
        params: new HttpParams().set('u', search)
      });
    } else return undefined;
  }



    
  newChat(me: User, chatName: string, users: string[]): Observable<HttpResponse<ChatData[]>> | undefined  {
    console.log('ConnectionService.newChat()')
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
    console.log('ConnectionService.getChats()')
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
    console.log('ConnectionService.getChatData()')
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



  
  leaveChat( chatId: string): Observable<HttpResponse<any>> | undefined {
    console.log('ConnectionService.leaveChat()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      return this.http.delete<any>(server.getURIwithPath(`/user/${this.userObj.userId}/chats/${chatId}`), {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  getChatUsers(chatId: string): Observable<HttpResponse<User[]>> | undefined {
    console.log('ConnectionService.getChatUsers()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      return this.http.get<User[]>(server.getURIwithPath(`/user/${this.userObj.userId}/chats/${chatId}/users`), {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  setChatSettings(chat: Chat): Observable<HttpResponse<any>> | undefined {
    console.log('ConnectionService.getChatSettings()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj && token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${this.userObj.userId}/chats/${chat.chatId}/chatSettings`), chat, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }


  
// userId: string, login: string,
  addUsersToChat( chatId: string, chatName: string, userIds: string[], pOffsets: PartitionOffset[]): Observable<HttpResponse<any>> | undefined {
    console.log('ConnectionService.addUsersToChat()')
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( this.userObj &&token && server ) {
      const body = {
        invitersLogin: this.userObj.login,
        chatName: chatName,
        users: userIds,
        partitionOffsets: pOffsets
      }
      return this.http.post<any>(server.getURIwithPath(`/user/${this.userObj.userId}/chats/${chatId}/addNewUsers`), body, {
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
      console.log(`ConnectionService.connectViaWS() -> Initializing Connection via WebSocket to: ws://${server.getURIwithoutProtocol()}`)
      this.wsConnection = new WebSocket(`ws://${server.getURIwithoutProtocol()}/user/${conf.me.userId}/ws`);
      // this.wsConnection = new WebSocket(`ws://localhost:9000/user/${conf.me.userId}/ws`);
      this.wsConnection.onopen = () => {
        console.log('ConnectionService.connectViaWS() onOpen -> WebSocket connection opened.');
        this.wsConnection?.send( JSON.stringify( conf ) )
        if ( this.reconnectWS ) {
          this.responseNotifier.printNotification(
            {
              header: 'Information',
              // code: 0,
              message: 'Connection retrieved.'
            }
          )
        }
        // we mark that when we loose ws connection, 
        // we need to try to reconnect
        this.reconnectWS = true
        // stopping attempts to reconect
        this.restartWS( false )
        this.writerCleaner = setInterval(() => {
          if ( this.someoneIsWriting ) this.chatService.showWriting( undefined )
          this.someoneIsWriting = false
        }, 1200)

      };
      this.wsConnection.onmessage = (msg: any) => {
        
        const body = JSON.parse( msg.data )
        if ( body.conf ) {
          console.log('ConnectionService.wsConnection.onMessage -> getConfig.', body.conf);
        }          
        if ( body.newMsgList ) {
          console.log('ConnectionService.wsConnection.onMessage -> get NEW messages.', body.newMsgList);
          this.chatService.insertNewMessages2( body.newMsgList )
        }
        if ( body.oldMsgList ) {
          console.log('ConnectionService.wsConnection.onMessage -> get OLD messages.', body.oldMsgList);
          this.chatService.insertOldMessages( body.oldMsgList )
        }
        if ( body.inv ) {
          console.log('ConnectionService.wsConnection.onMessage -> get INVITATION .', body.inv);
          this.handleInvitation( body.inv )
        }
        if ( body.wrt ) {
          if (body.wrt.writerId != this.userObj?.userId) {
            this.someoneIsWriting = true
            this.chatService.showWriting( body.wrt )
          }          
        }
        if (body.comm == 'opened correctly') {
          console.log('ConnectionService.wsConnection.onMessage -> WS connection opened correctly.');
          // starting ping via WS to keep connection alive
          this.startPingSender()
          this.updateSession()
          this.serviceInitializedEmitter.emit( 0 )
        }
        if ( body.num && body.message) {
          console.error('ConnectionService.wsConnection.onMessage -> Got some error in backend.', body.message);
        }
      }
      this.wsConnection.onclose = () => {
        console.log('ConnectionService.wsConnection.onclose -> WS connection CLOSED correctly.');
        if ( this.wsConnection ) this.wsConnection = undefined
        if ( this.wsPingSender ) {
          console.log('ConnectionService.wsConnection.onclose -> clearing/stopping wsPingSender.');
          clearInterval( this.wsPingSender )
          this.wsPingSender = undefined
        }
        if ( this.reconnectWS ) {
          // if we keep WS connection we should try to connect to other backend serwer.
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
        } else console.warn('ConnectionService.wsConnection.onclose -> reconnectWS should be set to TRUE.');
      };
      this.wsConnection.onerror = (error) => {
        console.error('ConnectionService.wsConnection.onerror -> WS connection returned error ???', error);
        if ( this.wsConnection ) this.wsConnection = undefined
        // here probably we should close connection ??? and restart it ???
      };
    }
  }


  private restartWS(b: boolean) {
    if ( b ) {
      if ( ! this.reconnectWSTimer ) {
        console.log('ConnectionService.restartWS() -> initializing reconnectWSTimer Interval.');
        this.reconnectWSTimer = setInterval( () => {
          console.log('ConnectionService.reconnectWSTimer -> Trying to reconnect WS.');
          this.connectViaWebsocket()
        }, 2000) // we try reconnect every 2s
      }          
    } else {
      // if we get false this means that we need to stop timer,
      // because we connected, 
      // or we disconnected and we do not need to reconnect
      if ( this.reconnectWSTimer ) {
        console.log('ConnectionService.restartWS() -> clearing reconnectWSTimer Interval.');
        clearInterval( this.reconnectWSTimer )
        this.reconnectWSTimer = undefined
      }
    }
  }




  // methods sending different data via WS to backend


  private sendPoisonPill() {
    if (this.wsConnection) {
      console.log('ConnectionService.sendPoisonPill() -> via WS');
      this.wsConnection.send('PoisonPill');
    } else {
      console.error('ConnectionService.sendPoisonPill() -> Cannot send data via WS, open a connection first');
    }
  }




  sendMessage(m: Message) {
    if (this.wsConnection) {
      console.log('ConnectionService.sendMessage() -> via WS');
      const body = {
        user:    this.userObj,
        message: m
      }
      this.wsConnection.send(JSON.stringify( body ));
    } else {
      console.error('ConnectionService.sendMessage() -> Cannot send data via WS, open a connection first');
    }
  }


  
  /*
    DEPRECATED

    metoda nie jest używana bo, zaproszenia są wysyłane automatycznie przez backend 
    jak tylko czat zostanie poprawnie utworzony. 
  */
  /* private sendInvitation(inv: Invitation) {
    if (this.wsConnection) {
      console.log('sending invitation to server.');
      this.wsConnection.send(JSON.stringify( inv ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }  */




  handleInvitation(invitation: Invitation) {
    if ( this.userObj ) {
      console.log('ConnectionService.handleInvitation()');
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
                console.log('ConnectionService.handleInvitation() -> getChatData().subscribe()');
                this.chatService.addNewChat( cd )
                this.startListeningFromNewChat( cd.chat.chatId, cd.partitionOffsets )
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
                          console.log('ConnectionService.handleInvitation() -> getChatData().subscribe() -> updateJoiningOffset().subscribe()', invitation.myJoiningOffset);
                        }
                      },
                      error: (err) => {
                        console.error('ConnectionService.handleInvitation() -> getChatData().subscribe() -> updateJoiningOffset().subscribe()', err);
                      },
                      complete: () => {}
                    })
                  }
                }                  
              }                
            }              
          },
          error: (err) => {
            console.error('ConnectionService.handleInvitation() -> getChatData().subscribe()', err);
          },
          complete: () => {}
        })
      }
    }    
  }



  sendWriting(w: Writing) {
    if (this.wsConnection) {
      console.log('sending writing to server.');
      this.wsConnection.send(JSON.stringify( w ));
    } else {
      console.error('ConnectionService.sendWriting() -> Cannot send data via WS, open a connection first.');
    }
  }




  private sendChatOffsetUpdate(u: ChatOffsetUpdate) {
    if (this.wsConnection) {
      console.log('ConnectionService.sendChatOffsetUpdate() -> sending update to backend via WS.');
      this.wsConnection.send(JSON.stringify( u ));
    } else {
      console.error('ConnectionService.sendChatOffsetUpdate() -> Cannot send data via WS, open a connection first.');
    }
  }



  
  updateSession() {
    if (this.wsConnection && this.userObj) {
      console.log('ConnectionService.updateSession() -> sending update to backend via WS.');
      this.session.updateSession(this.userObj.userId)
      const ksid = this.session.getKsid()
      if ( ksid ) { 
        const session = {
          sessionId: ksid.sessId,
          userId:    ksid.userId,
          validityTime: ksid.validityTime
        }
        this.wsConnection.send(JSON.stringify( session ));
      }
    } else {
      console.error('ConnectionService.updateSession() -> Cannot send data via WS, open a connection first.');
    }  
  }





  
  startListeningFromNewChat(chatId: string, partOffsets: PartitionOffset[]) {
    if (this.wsConnection) {
      console.log('ConnectionService.startListeningFromNewChat()');
      const body = {
        chatId: chatId,
        partitionOffset: partOffsets
      }
      this.wsConnection.send(JSON.stringify( body ));
    } else {
      console.error('ConnectionService.startListeningFromNewChat() -> Cannot send data via WS, open a connection first.');
    }  
  }




  private fetchOlderMessages(chatId: string) {
    if (this.wsConnection) {
      console.log('ConnectionService.fetchOlderMessages()');
      const body = { chatId: chatId }
      this.wsConnection.send(JSON.stringify( body )); 
    } else {
      console.error('ConnectionService.fetchOlderMessages() -> Cannot send data via WS, open a connection first.');
    } 
  }

  


  private startPingSender() {
    console.log('ConnectionService.startPingSender()');
    this.wsPingSender = setInterval(() => {
      if ( this.wsConnection ) {
        console.log('ConnectionService.wsPingSender -> sending ping via WS.');
        this.wsConnection.send('ping')
      }      
    }, 60000 ) // ping 'ping' message every 1 minute
  }



  // DEPRECATED
  /* private closeWebSocket() {
    if (this.wsConnection) {
      this.reconnectWS = false
      console.log('sending PoisonPill to server.');
      this.wsConnection.send('PoisonPill')
      this.someoneIsWriting = false
      if ( this.writerCleaner ) { 
        clearTimeout( this.writerCleaner )
        this.writerCleaner = undefined
      }
      this.sendPoisonPill()
      this.wsConnection.close()
      console.log('connection deeactivated.');
      this.wsConnection = undefined;
    }
  }
 */




  isWSconnected(): boolean {
    if ( this.wsConnection ) {
      console.log('ConnectionService.isWSconnected() -> true');
      const state =  this.wsConnection.readyState
      return state == this.wsConnection.OPEN
    } else {
      console.log('ConnectionService.isWSconnected() -> false');
      return false 
    }
  }




  isWSconnectionDefined(): boolean {
    console.log(`ConnectionService.isWSconnectionDefined() -> ${this.wsConnection !== undefined}`);
    return this.wsConnection !== undefined
  }



  getUser(): User | undefined {
    console.log(`ConnectionService.getUser() -> ${this.userObj}`);
    return this.userObj
  }

  updateUserLogin(newLogin: string) {
    if ( this.userObj ) {
      // muszę przenieść tę funkcję do Settings service. 
      this.userObj.login = newLogin
      console.log(`ConnectionService.updateUserLogin() -> ${this.userObj}`);
      this.chatService.setUser( this.userObj )
      this.settingsService.setUser( this.userObj ) 
      // chyba jakaś notyfikacja, że mój login został zmieniony
    }
  }




  // closing methods


  clearSubscriptions() {
    console.log(`ConnectionService.clearSubscriptions() `);

    if ( this.logoutSubscription )             this.logoutSubscription.unsubscribe()
    if ( this.chatOffsetUpdateSubscription )   this.chatOffsetUpdateSubscription.unsubscribe()
    if ( this.fetchOlderMessagesSubscription ) this.fetchOlderMessagesSubscription.unsubscribe() 
    
    this.logoutSubscription = undefined
    this.chatOffsetUpdateSubscription = undefined
    this.fetchOlderMessagesSubscription = undefined
  }





  closeAllSubServices() {
    console.log(`ConnectionService.closeAllSubServices() `);
    this.chatService.clear()
    this.session.clearService()
  }





  disconnect() {
    console.log(`ConnectionService.disconnect() `);
    this.sendPoisonPill()
    this.closeAllSubServices();
    this.clearSubscriptions();
    this.session.invalidateSession()
    if ( this.wsPingSender ) {
      console.log(`ConnectionService.disconnect() -> clearing wsPingSender Interval`);
      clearInterval( this.wsPingSender )
      this.wsPingSender = undefined
    }
    if( this.reconnectWSTimer ){
      console.log(`ConnectionService.disconnect() -> clearing reconnectWSTimer Interval`);
      clearInterval( this.reconnectWSTimer )
      this.reconnectWSTimer = undefined
    }
    if( this.writerCleaner ){
      console.log(`ConnectionService.disconnect() -> clearing writerCleaner Interval`);
      clearInterval( this.writerCleaner )
      this.writerCleaner = undefined
    }
    this.reconnectWS  = false
    this.wsConnection?.close()
    this.wsConnection = undefined
    this.initialized  = false
    this.userObj      = undefined
    
  }            
  

}

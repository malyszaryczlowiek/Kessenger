import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, Inject, EventEmitter } from '@angular/core';

import { Observable, of } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

import { Invitation } from '../models/Invitation';
import { Message } from '../models/Message';
import { User } from '../models/User';
import { Settings } from '../models/Settings';
import { Writing } from '../models/Writing'; 
import { ChatData } from '../models/ChatData';
import { SessionService } from './session.service';
import { Chat } from '../models/Chat';
import { Configuration } from '../models/Configuration';
import { ChatOffsetUpdate } from '../models/ChatOffsetUpdate';
import { PartitionOffset } from '../models/PartitionOffset';
import { UserOffsetUpdate } from '../models/UserOffsetUpdate';
import { LoadBalancerService } from './load-balancer.service';
import { ResponseNotifierService } from './response-notifier.service';



@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  
  private wsConnection:  WebSocket      | undefined
  private wsPingSender:  NodeJS.Timeout | undefined
  
  private writerCleaner: NodeJS.Timeout | undefined
  private someoneIsWriting: boolean = false

  private reconnectWS = true
  private myUserId: string | undefined

  public newMessagesEmitter: EventEmitter<Array<Message>>      = new EventEmitter<Array<Message>>()
  public oldMessagesEmitter: EventEmitter<Array<Message>>      = new EventEmitter<Array<Message>>()
  public invitationEmitter:  EventEmitter<Invitation>          = new EventEmitter<Invitation>()
  public writingEmitter:     EventEmitter<Writing | undefined> = new EventEmitter<Writing| undefined>()
  public restartWSEmitter:   EventEmitter<boolean>             = new EventEmitter<boolean>()
  public wsConnEmitter:      EventEmitter<boolean>             = new EventEmitter<boolean>()


  constructor(private http: HttpClient, 
              // @Inject("API_URL") private api: string,
              private responseNotifier: ResponseNotifierService,
              private loadBalancer: LoadBalancerService,
              private session: SessionService) { }


  disconnect() {
    this.reconnectWS = false
    this.session.invalidateSession()
    if ( this.wsPingSender ) {
      clearInterval( this.wsPingSender )
      this.wsPingSender = undefined
    }
    this.sendPoisonPill()
    this.wsConnection?.close()
    this.wsConnection = undefined
  }            
  // {user: User, settings: Settings, chatList: Array<{chat: Chat, partitionOffsets: Array<PartitionOffset>}>}


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



  
  changeSettings(userId: string, s: Settings): Observable<HttpResponse<any>> | undefined  {
    const token = this.session.getSessionToken()
    const server = this.loadBalancer.currentServer
    if ( token && server ) {
      return this.http.put<any>(server.getURIwithPath(`/user/${userId}/changeSettings`), s, { 
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
    const token = this.session.getSessionToken()
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


  connectViaWS(conf: Configuration) {
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
        this.restartWSEmitter.emit( false )

        //tutaj //  zdefiniować cleaner, który następnie będzie usuwany w metodzie 
        // cleaner musi wysyłać wiadomość tylko jak zmienna 
        // someoneIsWriting jest na true
        // emitter wysyła wtedy po 0.5 s undefined
        // jeśli jest na false to emitter nie wysyła nic 
        this.writerCleaner = setInterval(() => {
          if ( this.someoneIsWriting ) this.writingEmitter.emit( undefined )
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
          this.newMessagesEmitter.emit( body.newMsgList )
        }
        if ( body.oldMsgList ) {
          console.log('got list of OLD message: ', body.oldMsgList )
          this.oldMessagesEmitter.emit( body.oldMsgList )
        }
        if ( body.inv ) {
          console.log('got invitation: ', body.inv )
          this.invitationEmitter.emit( body.inv )
        }
        if ( body.wrt ) {
          // console.log('got writing: ', body.wrt )
          if (body.wrt.writerId != this.myUserId) {
            this.someoneIsWriting = true
            this.writingEmitter.emit( body.wrt )
          }          
        }
        if (body.comm == 'opened correctly') {
          console.log('WS connection opend correctly.')
          this.wsConnEmitter.emit( this.isWSconnected() )
          this.startPingSender()
        }
        if (body.num && body.message) {
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
          this.restartWSEmitter.emit( this.reconnectWS )
        } else console.error('reconnectWS is set to FALSE')
      };
      this.wsConnection.onerror = (error) => {
        console.error('error from WS connection', error)
        // here probably we should close connection ??? and restart it ???
      };
    }
  }



  // method closes akka actor and ws connection
  sendPoisonPill() {
    if (this.wsConnection) {
      console.log('sending PoisonPill to server.');
      this.wsConnection.send('PoisonPill');
    } else {
      console.error('WS connection is closed now. ');
    }
  }




  sendMessage(message: Message) {
    if (this.wsConnection) {
      console.log('sending data to server.');
      this.wsConnection.send(JSON.stringify( message ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }


  

  sendInvitation(inv: Invitation) {
    if (this.wsConnection) {
      console.log('sending invitation to server.');
      this.wsConnection.send(JSON.stringify( inv ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  } 




  sendWriting(w: Writing) {
    if (this.wsConnection) {
      console.log('sending writing to server.');
      this.wsConnection.send(JSON.stringify( w ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }




  sendChatOffsetUpdate(u: ChatOffsetUpdate) {
    if (this.wsConnection) {
      console.log('sending offset update to server.');
      this.wsConnection.send(JSON.stringify( u ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }



  
  updateSession(sendUpdateToServer: boolean) {
    if (this.wsConnection && this.myUserId) {
      console.log('sending session update to server.');
      this.session.updateSession(this.myUserId)
      const ksid = this.session.getKsid()
      if (ksid && sendUpdateToServer) {
        const session = {
          sessionId: ksid.sessId,
          userId: ksid.userId,
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




  fetchOlderMessages(chatId: string) {
    if (this.wsConnection) {
      console.log('sending request to fetch older messages.');
      const body = { chatId: chatId }
      this.wsConnection.send(JSON.stringify( body )); 
    } else {
      console.error('Did not send data, open a connection first');
    } 
  }




  startPingSender() {
    this.wsPingSender = setInterval(() => {
      if ( this.wsConnection )
        this.wsConnection.send('ping')
    }, 60000 ) // ping empty message every 1 minute
  }




  closeWebSocket() {
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




  getUserId(): string | undefined {
    return this.session.getSavedUserId();
  }



  isWSconnected(): boolean {
    if ( this.wsConnection ) {
      const state =  this.wsConnection.readyState
      return state == this.wsConnection.OPEN
    } else return false 
  }




  isWSconnectionDefined(): boolean {
    return this.wsConnection !== undefined
  }






  /*
    KSID methods
  */

  isSessionValid(): boolean {
    return this.session.isSessionValid();
  }  



  // use sendSessionUpdate() insted

  /* updateSession(userId: string) {
    this.session.updateSession(userId); 
  } */



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




}

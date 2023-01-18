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
import { MessagePartOff } from '../models/MesssagePartOff';
import { Configuration } from '../models/Configuration';
import { ChatOffsetUpdate } from '../models/ChatOffsetUpdate';
import { PartitionOffset } from '../models/PartitionOffset';



@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  
  private wsConnection:     WebSocket      | undefined
  private wsPingSender:     NodeJS.Timeout | undefined
  
  private reconnectWS = true
  


  public messageEmitter:    EventEmitter<MessagePartOff>    = new EventEmitter<MessagePartOff>()
  public invitationEmitter: EventEmitter<Invitation>        = new EventEmitter<Invitation>()
  public writingEmitter:    EventEmitter<Writing>           = new EventEmitter<Writing>()

  public restartWSEmitter:  EventEmitter<boolean>           = new EventEmitter<boolean>()


  constructor(private http: HttpClient, 
              @Inject("API_URL") private api: string,
              private session: SessionService) { }


  disconnect() {
    this.messageEmitter.unsubscribe()
    this.invitationEmitter.unsubscribe()
    this.writingEmitter.unsubscribe()
    this.restartWSEmitter.unsubscribe()
    this.reconnectWS = false
    this.session.invalidateSession()
    this.sendPoisonPill()
    this.wsConnection?.close()
    this.wsConnection = undefined
  }            
  


  signUp(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined{
    const fakeUserId = uuidv4();
    this.session.setNewSession(fakeUserId); 
    const body = {
      login: login,
      pass: pass,
      userId: ''
    };
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.post<{user: User, settings: Settings}>(this.api + '/signup', body, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined
  }  




  signIn(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    const fakeUserId = uuidv4();
    this.session.setNewSession(fakeUserId); 
    const body = {
      login: login,
      pass: pass,
      userId: ''
    };
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.post<{user: User, settings: Settings}>(this.api + '/signin', body, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined
  }



  logout(): Observable<HttpResponse<string>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<string>(this.api + '/logout',{
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  user(userId: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<{user: User, settings: Settings}>(this.api + `/user/${userId}`, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    }
    else return undefined
  }



  // tuaj teraz naprawiać

  updateJoiningOffset(userId: string, newJoiningOffset: number): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.put<any>(this.api + `/user/${userId}/updateJoiningOffset`, { 
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json',
        params: {
          offset: newJoiningOffset
        }
      });
    }
    else return undefined
  }


  
  changeSettings(userId: string, s: Settings): Observable<HttpResponse<any>> | undefined  {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.put<any>(this.api + `/user/${userId}/changeSettings`, s, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }


  changeLogin(userId: string, newLogin: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.put<any>(this.api + `/user/${userId}/changeLogin`, newLogin, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }


  
  changePassword(userId: string, oldPassword: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      const body = {
        oldPass: oldPassword,
        newPass: newPassword
      }
      return this.http.put<any>(this.api + `/user/${userId}/changePassword`, body, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }



  searchUser(userId: string, search: string) : Observable<HttpResponse<User[]>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<User[]>(this.api + `/user/${userId}/searchUser`, { 
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
    if ( token ) {
      const body = {
        me: me,
        users: users,
        chatName: chatName
      }
      return this.http.post<ChatData[]>(this.api + `/user/${me.userId}/newChat`, body, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



  getChats(userId: string): Observable<HttpResponse<Array<ChatData>>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<Array<ChatData>>(this.api + `/user/${userId}/chats`, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  getChatData(userId: string, chatId: string): Observable<HttpResponse<{chat: Chat, partitionOffsets: Array<{partition: number, offset: number}>}>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<{chat: Chat, partitionOffsets: Array<{partition: number, offset: number}>}>(this.api + `/user/${userId}/chats/${chatId}`, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



  
  leaveChat(userId: string, chatId: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.delete<any>(this.api + `/user/${userId}/chats/${chatId}`, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  getChatUsers(userId: string, chatId: string): Observable<HttpResponse<User[]>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<User[]>(this.api + `/user/${userId}/chats/${chatId}/users`, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }




  setChatSettings(userId: string, chat: Chat): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.put<any>(this.api + `/user/${userId}/chats/${chat.chatId}/chatSettings`, chat, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }


  

  addUsersToChat(userId: string, login: string, chatId: string, chatName: string, userIds: string[], pOffsets: PartitionOffset[]): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      const body = {
        invitersLogin: login,
        chatName: chatName,
        users: userIds,
        partitionOffsets: pOffsets
      }
      return this.http.post<any>(this.api + `/user/${userId}/chats/${chatId}/addNewUsers`, body, {
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
    if (this.wsConnection === undefined) {
      console.log('Initializing Connection via WebSocket.')
      this.wsConnection = new WebSocket(`ws://localhost:9000/user/${conf.me.userId}/ws`);
      this.wsConnection.onopen = () => {
        console.log('WebSocket connection opened.');
        this.wsConnection?.send( JSON.stringify( conf ) )

        //w konfiguracji należy przesłać również informacje o sesji ???
        //tak aby server był w stanie sprawdzić czy user ma ważną sesję

        this.reconnectWS = true
        this.restartWSEmitter.emit( false )
      };
      this.wsConnection.onmessage = (msg: any) => {
        const body = JSON.parse( msg.data )
        if ( body.conf )
          console.log('got config: ', body.conf )

        if ( body.msg ) {
          console.log('got message: ', body.msg )
          this.messageEmitter.emit( body.msg )
        }          
        if ( body.inv ) {
          console.log('got invitation: ', body.inv )
          this.invitationEmitter.emit( body.inv )
        }
        if ( body.wrt ) {
          console.log('got writing: ', body.wrt )
          this.writingEmitter.emit( body.wrt )
        }
        if (body.comm == 'opened correctly') {
          console.log('WS connection opend correctly.')
          this.startPingSender()
        }
        else 
          console.log('got other message: ', body)
      }
      this.wsConnection.onclose = () => {
        console.log('WebSocket connection closed.');
        if ( this.wsConnection ) this.wsConnection = undefined
        if ( this.wsPingSender ) {
          clearInterval( this.wsPingSender )
          this.wsPingSender = undefined
        }
        if ( this.reconnectWS ) this.restartWSEmitter.emit( this.reconnectWS )
      };
      this.wsConnection.onerror = (error) => {
        console.error('error from web socket connection', error)
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
      console.error('Did not send data, open a connection first');
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


  // not necessary
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
      console.log('sending invitation to server.');
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

  
  startListeningFromNewChat(chatId: string, partOffsets: PartitionOffset[]) {
    if (this.wsConnection) {
      console.log('sending offset update to server.');
      const body = {
        chatId: chatId,
        partitionOffset: partOffsets
      }
      this.wsConnection.send(JSON.stringify( body ));
    } else {
      console.error('Did not send data, open a connection first');
    }  
  }

  startPingSender() {
    this.wsPingSender = setInterval(() => {
      if ( this.wsConnection )
        this.wsConnection.send('empty')
    }, 60000 ) // ping empty message every 1 minute
  }


  closeWebSocket() {
    if (this.wsConnection) {
      this.reconnectWS = false
      console.log('sending PoisonPill to server.');
      this.wsConnection.send('PoisonPill')
      //this.sendPoisonPill()
      this.wsConnection.close()
      console.log('connection deeactivated.');
      this.wsConnection = undefined;
    }
  }



  getUserId(): string | undefined {
    return this.session.getSavedUserId();
  }







  /*
    KSID methods
  */

  isSessionValid(): boolean {
    return this.session.isSessionValid();
  }  



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








  

















  /*

     Methods to delete

  */





// todo na dzisiaj
// 1. napisać rządanie które będzie wysyłało w nagłówku header ksid
// 2 napisać w backendzie endpoint przyjmujący rządanie z nagłówkiem ksid
// 3. dodać obiekt ksid, który będzie sprawdzany 



    // returns observable
    getUsers(): Observable<User[]> {
      let array = new Array<User>();
      this.http.get<User[]>( this.api + '/angular/users',  //this.api
        {
          headers: new HttpHeaders()
            .set('MY_KESSENGER_HEADER', 'true'),
          observe: 'response', 
          responseType: 'json'
        })
        .subscribe( {
          // on normal response
          next: (response) => {
            // this.userss = 
            if (response.body) {
              console.log('body is not null');
              response.body.forEach(u => array.push(u));
              
              // this.users$ = of()
              //this.userss.concat(response.body)
            } else {
              console.warn('body is NULL.')
            }
            
            /* response.headers.keys.arguments.forEach((key: string) => {
              console.log('header key: ' + key)
            }); */
            console.warn('headers size: ' + response.headers.keys.length)
            let k = response.headers.get('Set-Cookie')
            if (k) { console.log('set cookie: ' + k); }
            else console.log('has not COOKIE')
  
            console.log('headers: ' + response.headers.getAll)  
/*             const play = this.cookie.get('PLAY_SESSION')
            console.log(`PLAY_SESSION: ${play}`)

            const kes = this.cookie.get('KESSENGER_SID')
            console.log(`KESSENGER_SID: ${kes}`) */
    
            //console.log('status is: ' + response.status) 
          } , 
    
          // on error
          error: (error) => console.error(error),       
    
          // on complete
          complete: () => console.log('Request Completed')       
        })
      return of(array);  
    }
  
  
  
  
    postNothing(): Observable<string> {
      const options = {
        responseType: 'text' as const,
      };
      return this.http.post(this.api + '/angular/post',{},{responseType:'text'});
    }

    postUser(): Observable<string> {
      const userToSend: User = {
        login: 'login',
        userId: uuidv4()
      }
      return this.http.post(this.api + '/jsonpost', userToSend , {responseType:'text'});
    }

    getStream(): Observable<User[]> {
      return this.http.get<User[]>( this.api + '/angular/users/stream')
    }

    callAngular() {
      this.http.get<string>(this.api + '/angular/users').subscribe()
    }



  parseToMessage(m: any): Message | undefined {
    try {
      const body = JSON.parse( m )
      
      const content: string | null     = body.content;
      const authorId: string | null    = body.authorId;
      const authorLogin: string | null = body.authorLogin;
      const chatId: string | null     = body.chatId;
      const chatName: string   | null = body.chatName;
      const groupChat: boolean | null = body.groupChat;    
      const utcTime: number | null    = body.utcTime;
      const zoneId: string | null     = body.zoneId;
      
      const isValid: boolean = (content != null) && 
        (authorId != null) && 
        (authorLogin != null) &&
        (chatId != null) && 
        (chatName != null) && 
        (groupChat != null) &&
        (utcTime != null) &&
        (zoneId != null)
      if ( isValid ) 
        console.log('Message is valid')
      else
        console.log('Message is NOT valid')





      const p: Message | undefined = <Message> JSON.parse( m ) as Message | undefined
      return m 
    } catch (error){
      console.log(`cannot parse to selected type`, error)
      return undefined
    }
  }

  parseToInvitation(m: any): Invitation | undefined {
    try {
      const p: Invitation | undefined =  <Invitation>  m  as Invitation | undefined
      return m 
    } catch (error){
      console.log(`cannot parse to selected type`, error)
      return undefined
    }
  }

   parseTo<T>(m: any): T | undefined {
    try {
      const p: T | undefined = <T> m as T | undefined  // m as T
      return p
    } catch (error){
      console.log(`cannot parse to selected type`, error)
      return undefined
    }
  } 
}

import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, Inject, EventEmitter } from '@angular/core';

import { Observable, of } from 'rxjs';

import { v4 as uuidv4 } from 'uuid';

import { Chat } from '../models/Chat';
import { Invitation } from '../models/Invitation';
import { Message } from '../models/Message';
import { User } from '../models/User';
import { Settings } from '../models/Settings';
import { Writing } from '../models/Writing';

import { SessionService } from './session.service';
import { ChatData } from '../models/ChatData';
// import * as SockJS from 'sockjs-client';




@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  
  private wsConnection: WebSocket | undefined
  public messageEmitter:    EventEmitter<Message>    = new EventEmitter<Message>()
  public invitationEmitter: EventEmitter<Invitation> = new EventEmitter<Invitation>()
  public writingEmitter:    EventEmitter<Writing>    = new EventEmitter<Writing>()


  constructor(private http: HttpClient, 
              @Inject("API_URL") private api: string,
              private session: SessionService) { }


  disconnect() {
    this.messageEmitter.unsubscribe()
    this.invitationEmitter.unsubscribe()
    this.writingEmitter.unsubscribe()
    this.session.invalidateSession()
    this.wsConnection?.close()
    this.wsConnection = undefined
  }            
  


  signUp(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined{
    // we set cookie.
    const fake = uuidv4();
    this.session.setNewSession(fake); // todo zmienić bo tutuaj nie będziemy jezcze tworzyli id użytkowanika. 

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
    const fake = uuidv4();
    this.session.setNewSession(fake); 
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



  logout(): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<any>(this.api + '/logout',{
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



  // todo zmienić typy zwracany
  
  changeSettings(s: Settings): Observable<HttpResponse<any>> | undefined  {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.post<any>(this.api + '/user', s, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }


  // todo zmienić typy zwracany

  changeMyLogin(userId: string, newLogin: string): Observable<HttpResponse<any>> | undefined {
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


  changePassword(userId: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.put<any>(this.api + `/user/${userId}/changePassword`, newPassword, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json'
      });
    } else return undefined;
  }


  searchUser(userId: string, search: string) : Observable<HttpResponse<User>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<User>(this.api + `/user/${userId}/searchUser`, { 
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response',
        responseType: 'json',
        params: new HttpParams().set('u', search)
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


  // nieużywana
  getChatUsers(userId: string, chatId: string): Observable<HttpResponse<User[]>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.get<User[]>(this.api + `/user/${userId}/chats/${chatId}`, {
        headers:  new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



  
  // nieużywana
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




  // nieużywana
  updateChatName(userId: string, chatId: string, newChatName: string): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      return this.http.put<any>(this.api + `/user/${userId}/chats/${chatId}/newChatName`, newChatName, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



  addUsersToChat(userId: string, chatId: string, chatName: string, userIds: string[]): Observable<HttpResponse<any>> | undefined {
    const token = this.session.getSessionToken()
    if ( token ) {
      const body = {
        chatName: chatName,
        users: userIds
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
    OD TEGO ENDPOINTU KONTYNUOWAĆ SPRAWDZANIE
  */
  newChat(me: User, chatId: string, chatName: string, users: string[]): Observable<HttpResponse<Chat>> | undefined  {
    const token = this.session.getSessionToken()
    if ( token ) {
      const body = {
        me: me,
        users: users,
        chatName: chatName
      }
      return this.http.post<Chat>(this.api + `/user/${me.userId}/chats/${chatId}/newChat`, body, {
        headers: new HttpHeaders()
          .set('KSID', token),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
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





  






  // w tej metodzie generujemy ciasteczko i zapisujemy je w przeglądarce. 
  setNewKSID(userId: string) {
    this.session.setNewSession(userId);
  }




  getSessionToken(): string | undefined {
    return this.session.getSessionToken();
  }




  invalidateSession() {
    this.session.invalidateSession();
  }


  createChat() {

  }






  /*
  WEBSOCKET
  */


  connectViaWS() {
    if (this.wsConnection === undefined) {
      console.log('Initializing Connection via WebSocket.')
      this.wsConnection = new WebSocket("ws://localhost:9000/angular/ws");
      this.wsConnection.onopen = () => {
        console.log('WebSocket connection opened.');


        // todo tutaj trzeba wysłać widaomość z konfiguracją 
        // czyli wszystkie chaty (dokładniej chatId)
        // jak ma być skonfigurowany actor żeby pobierał
        // właściwe dane


      };
      this.wsConnection.onmessage = (msg: any) => {
        console.log('Got Message', msg)
        
        const body = JSON.parse(msg)
        
        //const writing = this.parseTo<Writing>( body )
        //if (writing) this.writingEmitter.emit(writing)
        
        const message = this.parseTo<Message>( body )
        if (message) this.messageEmitter.emit(message)
        
        //const invitation = this.parseTo<Invitation>( body )
        //if (invitation) this.invitationEmitter.emit(invitation)
      }
      this.wsConnection.onclose = () => {
        console.log('WebSocket connection closed.');
      };

      this.wsConnection.onerror = (error) => {
        console.error('error from web socket', error)


        // tutaj nie wiem czy nie powiiniśmy użyć kolejnego emittera
        // i jak tylko pojawia się error to 
        // zamknąć połączenie i pobnownie otworzyć. 

      };



      console.log('Tworzenie WebSocket skończone')
    }
  }


  parseTo<T>(m: any): T | undefined {
    try {
      const p: T = m
      return p
    } catch (error){
      console.log(`cannot parse to selected type`, error)
      return undefined
    }
  }



  sendMessage(msg: any) {
    if (this.wsConnection) {
      console.log('sending data to server.');
      this.wsConnection.send(JSON.stringify( msg ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  }


  // not necessary
  /* sendInvitation(msg: Invitaiton) {
    if (this.wsConnection) {
      console.log('sending data to server.');
      this.wsConnection.send(JSON.stringify( msg ));
    } else {
      console.error('Did not send data, open a connection first');
    }
  } */


  closeWebSocket() {
    if (this.wsConnection) {
      this.wsConnection.close()
      console.log('connection deeactivated.');
      this.wsConnection = undefined;
    }
  }






















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
}

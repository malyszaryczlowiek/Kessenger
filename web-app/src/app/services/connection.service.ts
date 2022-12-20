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

// import * as SockJS from 'sockjs-client';




@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  
  private wsConnection:     WebSocket | undefined
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






  
  // start_here
  /* 
  todo teraz sprawdzić czy wszystko z websocket jest ok 
  a następnie w userService przepisać te metody wyżej które mają 
  używać tych endpointów.  
  */



  /*
  WEBSOCKET
  */


  // nalezy zimplemenować używanie websocketu
   // nieużywana

  connectViaWS(userId: string) {
    if (this.wsConnection === undefined) {
      console.log('Initializing Connection via WebSocket.')
      this.wsConnection = new WebSocket(`ws://localhost:9000/user/${userId}/ws`);
      this.wsConnection.onopen = () => {
        console.log('WebSocket connection opened.');
        // todo tutaj trzeba wysłać widaomość z konfiguracją 
        // czyli wszystkie chaty (dokładniej chatId)
        // jak ma być skonfigurowany actor żeby pobierał
        // właściwe dane
        this.wsConnection?.send('configuration')

        w konfiguracji należy przesłać również informacje o sesji ???
        tak aby server był w stanie sprawdzić czy user ma ważną sesję

      };
      this.wsConnection.onmessage = (msg: any) => {

        const body = msg.data
        const message = this.parseTo<Message>( body )
        if (message) {
          console.log('Got WS Message', message)
          // this.messageEmitter.emit( message )
        }
        const invitation = this.parseTo<Invitation>( body )
        if (invitation) {
          console.log('Got WS Message', invitation)
          //this.invitationEmitter.emit(invitation)
        } 



        // if (message) this.messageEmitter.emit( message )
        
        // następnie subskrybent w userService musi przechwycić taką wiadomość
        // dodać ją do odpowiedniego czatu a następnie musimy fetchować 
        // całą listę czatów na nowo ponieważ mogła się zmienić 
        // kolejność czatów na liscie 



        przygotować interfejsy w aktorze tak aby przetwarzał wszystkie rodzaje przychodzących wiadomości
        





      }
      this.wsConnection.onclose = () => {
        console.log('WebSocket connection closed.');
      };
      this.wsConnection.onerror = (error) => {
        console.error('error from web socket connection', error)
        // tutaj nie wiem czy nie powiiniśmy użyć kolejnego emittera
        // i jak tylko pojawia się error to 
        // zamknąć połączenie i pobnownie otworzyć. 
      };
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


  // method closes akka actor and ws connection
  sendPoisonPill() {
    if (this.wsConnection) {
      console.log('sending PoisonPill to server.');
      this.wsConnection.send('PoisonPill');
    } else {
      console.error('Did not send data, open a connection first');
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
  sendInvitation(msg: Invitation) {
    if (this.wsConnection) {
      console.log('sending invitation to server.');
      this.wsConnection.send(JSON.stringify( msg ));
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


  closeWebSocket() {
    if (this.wsConnection) {
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
}

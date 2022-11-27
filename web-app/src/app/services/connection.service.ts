import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';

import { Observable, of } from 'rxjs';

import { v4 as uuidv4 } from 'uuid';

import { Chat } from '../models/Chat';
import { User } from '../models/User';

import * as SockJS from 'sockjs-client';
import { Settings } from '../models/Settings';
import { SessionService } from './session.service';



@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  
  private wsConnection: WebSocket | undefined



  constructor(private http: HttpClient, 
              @Inject("API_URL") private api: string,
              private session: SessionService) { }
  


  signUp(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> {
    // we set cookie.
    const fake = uuidv4();
    this.session.setNewSession(fake); // todo zmienić bo tutuaj nie będziemy jezcze tworzyli id użytkowanika. 

    const body = {
      login: login,
      pass: pass,
      userId: ''
    };

    return this.http.post<{user: User, settings: Settings}>(this.api + '/signup', body, {
      headers:  new HttpHeaders()
        .set('KSID', this.session.getSessionToken()),
      observe: 'response', 
      responseType: 'json'
    });
  }  




  signIn(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> {
    const fake = uuidv4();
    this.session.setNewSession(fake); 

    const body = {
      login: login,
      pass: pass,
      userId: ''
    };

    return this.http.post<{user: User, settings: Settings}>(this.api + '/signin', body, {
      headers:  new HttpHeaders()
        .set('KSID', this.session.getSessionToken()),
      observe: 'response', 
      responseType: 'json'
    });
  }








  // todo zweryfikować
  getUserChats(userId: string): Observable<HttpResponse<Chat[]>> | undefined {
    if ( this.session.isSessionValid() ) {
      return this.http.get<Chat[]>(this.api + '/user/' + userId,{
        headers:  new HttpHeaders()
        .set('KSID', this.session.getSessionToken()),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }



/*     let token: string = '';
    if (this.session) {
      token = this.session.toString();
      
    } 
    else { // without ksid header request will be rejected
      return this.http.get<Chat[]>(this.api + '/user/' + userId,{
        observe: 'response', 
        responseType: 'json'
      })
    }    
 */  



  logout(): Observable<HttpResponse<any>> | undefined {
    if ( this.session.isSessionValid() ) {
      return this.http.get<any>(this.api + '/logout',{
        headers:  new HttpHeaders()
        .set('KSID', this.session.getSessionToken()),
        observe: 'response', 
        responseType: 'json'
      });
    } else return undefined;
  }













  getUserId(): string {
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




  getKSIDvalue(): string {
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
      console.log('tworzę SockJS')
      // todo ustawić time out
      this.wsConnection = new SockJS( this.api + '/angular/ws', {}, {}); //, {timeout: 10000}
      
      this.wsConnection.onopen = () => console.log('WebSocket connection opened.');
      this.wsConnection.onmessage = (msg: any) => console.log('Dpstałem wiadomość', msg);
      this.wsConnection.onclose = () => console.log('WebSocket connection closed.');
      console.log('Tworzenie SockJS skończone')
    }
  }


  sendMessage(msg: string) {
    if (this.wsConnection) {
      console.log('sending data to server.');
      this.wsConnection.send(msg);
    } else {
      console.error('Did not send data, open a connection first');
    }
  }


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

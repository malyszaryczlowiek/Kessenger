import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';

import { Observable, of } from 'rxjs';
import { CookieService } from 'ngx-cookie-service';
import { v4 as uuidv4 } from 'uuid';

import { User } from '../models/User';
import { UntypedFormBuilder } from '@angular/forms';
import { UtctimeService } from './utctime.service';


@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  private csrfToken: string = '';
  private ksid: string | undefined;

  private httpOptions = {
    headers: new HttpHeaders()
      .set('MY_KESSENGER_HEADER', 'true'),
    observe: 'response', 
    responseType: 'json'
  }


  /*
    jak tylko uruchamiamy servis to sprawdzamy czy
    mamy w przeglądarce zapisane ciasteczko jeśli nie mamy to 
      
  */
  constructor(private http: HttpClient, 
    @Inject("API_URL") private api: string,
    private cookieService: CookieService,
    private utcService: UtctimeService) 
    { 
      this.ksid = cookieService.get('ksid')
    }




  // w tej metodzie generujemy ciasteczko i zapisujemy je w przeglądarce. 
  saveKSID(userId: string, validityTime: number ) {
    const time: number = this.utcService.getUTCseconds();
    this.ksid = `${uuidv4()}__${userId}__${time}`;
    this.cookieService.set('ksid', this.ksid);
  }

  getKSID(): string {
    return this.cookieService.get('ksid');
  }

  removeKSID() {
    this.cookieService.delete('ksid');
  }

  updateKSID(userId: string, validityTime: number) {
    this.saveKSID(userId, validityTime);
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
        user_id: uuidv4()
      }
      return this.http.post(this.api + '/jsonpost', userToSend , {responseType:'text'});
    }

    getStream(): Observable<User[]> {
      return this.http.get<User[]>( this.api + '/angular/users/stream')
    }
}

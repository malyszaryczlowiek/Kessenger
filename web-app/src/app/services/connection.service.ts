import { ɵparseCookieValue } from '@angular/common';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable, of } from 'rxjs';

import { User } from '../models/User';




@Injectable({
  providedIn: 'root'
})
export class ConnectionService {

  private csrfToken: string = '';
  private sid: string = '';

  private httpOptions = {
    headers: new HttpHeaders()
      .set('MY_KESSENGER_HEADER', 'true'),
    observe: 'response', 
    responseType: 'json'
  }


  constructor(private http: HttpClient, @Inject("API_URL") private api: string) { }

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


}

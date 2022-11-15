import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ConnectionService } from './connection.service';

import { User } from '../models/User';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  
  // public userss: User[] = new Array<User>;




  constructor(private connection: ConnectionService) { }

  // handleResponse( response: HttpResponse<User[]>)


  getUsers() { 
    return this.connection.getUsers()
  }

  postNothing() {
    return this.connection.postNothing()
  }

}

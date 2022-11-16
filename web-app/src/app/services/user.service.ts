import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ConnectionService } from './connection.service';
import { v4 as uuidv4 } from 'uuid';

import { User } from '../models/User';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  
  

  public user: User | undefined;
  public chatAndUsers: Array<{chat: string, users: Array<User>}> = new Array()


  constructor(private connection: ConnectionService) { 
    this.initializeService()
  }


  initializeService() {
    console.log('UserService initialized')
  }


  // method called when session expires
  clearService() {
    this.chatAndUsers = new Array();
    this.connection.removeKSID;
    console.log('UserService clearservice')
  }


  signup() {

  }

  signin() {

  }




  

  createKSID(): string {
    this.connection.saveKSID(uuidv4(), 900);
    return this.connection.getKSID();
  }

  getRawKSID() {
    return this.connection.getKSID();
  }


  getUsers() { 
    return this.connection.getUsers()
  }

  postNothing() {
    return this.connection.postNothing();
  }

  postUser() {
    return this.connection.postUser();
  }
}

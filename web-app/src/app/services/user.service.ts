import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ConnectionService } from './connection.service';
import { v4 as uuidv4 } from 'uuid';

import { Chat } from '../models/Chat';
import { Message} from '../models/Message';
import { User } from '../models/User';
import { ActivatedRoute, Router } from '@angular/router';
import { ChatData } from '../models/ChatData';
import { Settings } from '../models/Settings';
import { HttpResponse } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  public user: User | undefined;
  public settings: Settings | undefined;

  public chatAndUsers: Array<ChatData> = new Array();

  


  constructor(private connection: ConnectionService, private router: Router) { 
    console.log('UserService constructor called.')
    if ( connection.hasKSID() ) {
      // try to load user's data.
      const uid = this.connection.getUserId();
      this.connection.getUserData(uid).subscribe( {
        // Jeśli mamy ksid i rządanie zostanie normalnie przetworzone to 
        // należy posortować 
        next: (response) => {
          
          // we need to sort our chats according to messageTime
          const chats = response.body 
          // we sort newest (larger lastMessageTime) first.
          if (chats) {
            this.chatAndUsers = chats.sort((a,b) => -(a.lastMessageTime - b.lastMessageTime))
            .map((chat, i, array) => {
              return {
                chat: chat,
                users: new Array<User>(),
                messages: new Array<Message>()
              }
            });
          }

          // update ksid
          

          // redirect to /user
          this.router.navigate(['user']);

        } ,
        error: (error) => {
          console.log(error) 
          // jeśli np otrzymamy error, że sesja jest już nieważna to należy 
          // usunąć niewazne ciasteczko i 
          
          this.connection.removeKSID();
          console.log('przekierowanie na stronę logownaia')
          this.router.navigate(['']);
        } ,
        complete: () => {}
      })
    } 
  }




  // method called when session expires
  clearService() {
    this.user = undefined;
    this.chatAndUsers = new Array();
    this.connection.removeKSID;
    console.log('UserService clearservice')
  }


  setUserAndSettings(u: User | undefined, s: Settings | undefined) {
    this.user = u;
    this.settings = s;
  }




  signUp(log: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> {
    const userId = uuidv4();
    this.user = {userId: userId, login: log};
    return this.connection.signUp(log, pass, userId);
  }




  signIn(login: string, pass: string) {
    // TODO dopisac min. przekierowanie na stronę /user
    //  jak użytkownik zostanie zalgowany poprawnie. 
    //  i pobierze wszystkie dane. 
    this.connection.signIn(login, pass)
  }


  createChat() {
    this.connection.createChat();
  }









  connectViaWebsocket() {
    this.connection.connectViaWS();
  }
  /*
  tutaj będzie musiał być parsing i obudowanie treści wiadomości 
  w inne informacje jak chat id user id etc. 
  */
  sendMessage(msg: string) {
    this.connection.sendMessage(msg);
  }

  closeWS() {
    this.connection.closeWebSocket();
  }



  
  callAngular() {
    this.connection.callAngular();
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
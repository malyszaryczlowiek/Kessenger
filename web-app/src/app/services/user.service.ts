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
import { UserSettingsService } from './user-settings.service';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  public user: User | undefined;
  public chatAndUsers: Array<ChatData> = new Array();

  public invitationEmitter = this.connection.invitationEmitter


  constructor(private connection: ConnectionService, private settings: UserSettingsService, private router: Router) { 
    console.log('UserService constructor called.')
    if ( this.connection.isSessionValid() ) {
      const userId = this.connection.getUserId();
      const c = this.connection.getUserChats(userId);
      if ( c ){
        c.subscribe( {
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
                };
              });
            }
  
            // update session
            this.connection.updateSession(userId);
  
            // redirect to /user
            this.router.navigate(['user']);
  
          } ,
          error: (error) => {
            console.log(error) 
            // jeśli np otrzymamy error, że sesja jest już nieważna to należy 
            // usunąć niewazne ciasteczko i 
            
            // this.connection.invalidateSession();
            this.clearService();
            console.log('przekierowanie na stronę logownaia')
            this.router.navigate(['']);
          } ,
          complete: () => {}
        })
      }
    }
    // todo 
    this.connection.messageEmitter.subscribe(
      (message: Message) => console.log(`message from emitter: ${message}`),
      (error) => console.log('Error in invitation emitter: ', error),
      () => console.log('on invitaion comoplented.')
    )

  }






  // method called when session expires
  clearService() {
    this.user = undefined;
    this.chatAndUsers = new Array();
    this.settings.clearSettings();
    this.connection.disconnect();
    console.log('UserService clearservice')
  }


  setUserAndSettings(u: User | undefined, s: Settings | undefined) {
    this.user = u;
    if (s) this.settings.setSettings(s);
  }

  updateSession() {
    if (this.user) {
      this.connection.updateSession(this.user.userId);
    }
  }

  isSessionValid(): boolean {
    return this.connection.isSessionValid();
  }










  signUp(log: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> {
    return this.connection.signUp(log, pass);
  }




  signIn(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> {
    return this.connection.signIn(login, pass);
  }


  logout() {
    const l = this.connection.logout();
    if ( l ) l.subscribe();
    this.clearService();
    this.router.navigate(['']);
  }




  getChats(): Observable<HttpResponse<Chat[]>> | undefined {
    if ( this.user ) {
      return this.connection.getUserChats(this.user?.userId);
    } else {
      this.clearService();
      return undefined;
    } 
  }







  createChat() {
    this.connection.createChat();
  }





  /*
    WEBSOCKET methods
  */



  connectViaWebsocket() {
    this.connection.connectViaWS();
  }
  /*
  tutaj będzie musiał być parsing i obudowanie treści wiadomości 
  w inne informacje jak chat id user id etc. 
  */
  sendMessage(msg: Message) {
    this.connection.sendMessage(msg);
  }

  closeWS() {
    this.connection.closeWebSocket();
  }



  
  callAngular() {
    this.connection.callAngular();
  }


  

  createKSID(): string {
    //this.connection.
    return this.connection.getKSIDvalue();
  }

  /* getRawKSID() {
    return this.connection.();
  } */


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

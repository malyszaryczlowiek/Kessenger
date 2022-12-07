import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ConnectionService } from './connection.service';
import { v4 as uuidv4 } from 'uuid';

import { Chat } from '../models/Chat';
import { Message} from '../models/Message';
import { User } from '../models/User';
import { Router } from '@angular/router';
import { ChatData } from '../models/ChatData';
import { Settings } from '../models/Settings';
import { HttpResponse } from '@angular/common/http';
import { UserSettingsService } from './user-settings.service';
import { Invitation } from '../models/Invitation';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  public user: User | undefined;
  public chatAndUsers: Array<ChatData> = new Array();


  // this probably may cause problems 
  public invitationEmitter = this.connection.invitationEmitter


  constructor(private connection: ConnectionService, private settings: UserSettingsService, private router: Router) { 
    console.log('UserService constructor called.')
    const userId = this.connection.getUserId();
    if ( userId ) {
      this.connection.updateSession(userId);
      console.log('KSID exists') 
      // we get user's settings 
      const s = this.connection.user(userId)
      if ( s ) {
        s.subscribe({
          next: (response) => {
            const body = response.body
            if ( body ){
              this.user = body.user
              this.settings.setSettings(body.settings)
              // this.connectViaWebsocket()
            }
            else {
              // print error message
              console.log(`ERROR /user/userId/settings: `)
            }
          },
          error: (error) => {
            

            console.log(error) 
            this.clearService()
            console.log('redirection to logging page')
            this.router.navigate([''])
          },
          complete: () => {}
        })
      }


      // we get user's chats
      const c = this.connection.getChats(userId);
      if ( c ){
        c.subscribe({
          next: (response) => {
            // we need to sort our chats according to messageTime
            const chats = response.body 
            // we sort newest (larger lastMessageTime) first.
            if (chats) {
              this.chatAndUsers = chats.sort((a,b) => -(a.chat.lastMessageTime - b.chat.lastMessageTime))
              .map((item, i, array) => {
                return {
                  chat: item.chat,
                  partitionOffsets: item.partitionOffsets,
                  users: item.users,
                  messages: item.messages
                };
              });
            }
            // redirect to /user
            this.router.navigate(['user']);
  
          } ,
          error: (error) => {
            console.log(error) 
            this.clearService()
            console.log('redirection to logging page')
            this.router.navigate([''])
          } ,
          complete: () => {}
        })
      }
    }


    this.connection.messageEmitter.subscribe(
      (message: Message) => {
        console.log(`message from emitter: ${message}`)
      },
      (error) => console.log('Error in message emitter: ', error),
      () => console.log('on message emitter completed.')
    )
    // the same for invitation and 

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






  // Server calls 



  signUp(log: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    return this.connection.signUp(log, pass);
  }




  signIn(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    return this.connection.signIn(login, pass);
  }


  logout() {
    const l = this.connection.logout();
    if ( l ) l.subscribe({
      next: (response) => {
        console.log('successfull logout from server')
      },
      error: (error) => console.error(error),
      complete: () => {
        console.log('logout() completed.')
      }
    });
    this.clearService();
    this.router.navigate(['']);
  }



  getUser(): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    if (this.user){ 
      this.connection.updateSession(this.user.userId)
      return this.connection.user(this.user.userId)
    }
    else 
      return undefined
  }


  changeSettings(s: Settings): Observable<HttpResponse<any>> | undefined {
    if (this.user)  {
      this.connection.updateSession(this.user.userId)
      return this.connection.changeSettings(this.user.userId, s)
    }      
    else 
      return undefined
  }


  changeLogin(newLogin: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.connection.updateSession(this.user.userId)
      return this.connection.changeLogin(this.user.userId, newLogin)
    }
    else
      return undefined
  }



  changePassword(oldPassword: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.connection.updateSession(this.user.userId)
      return this.connection.changePassword(this.user.userId, oldPassword, newPassword);
    }
    else return undefined;  
  }


  searchUser(search: string): Observable<HttpResponse<User[]>> | undefined {
    if (this.user) {
      this.connection.updateSession(this.user.userId)
      return this.connection.searchUser(this.user.userId, search);
    }
    else return undefined;
  }

  // chats
  

  getChats(): Observable<HttpResponse<ChatData[]>> | undefined {
    if ( this.user ) {
      this.connection.updateSession(this.user.userId)      
      return this.connection.getChats(this.user.userId);
    }
    else return undefined;
  }


  getChatUsers(chatId: string): Observable<HttpResponse<User[]>> | undefined {
    if (this.user) {
      this.connection.updateSession(this.user.userId)      
      return this.connection.getChatUsers(this.user.userId, chatId);
    }
    else return undefined;
  }


  leaveChat(chatId: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.connection.updateSession(this.user.userId)      
      return this.connection.leaveChat(this.user.userId, chatId);
    }
    else return undefined;
  }


  updateChatName(chatId: string, newName: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.connection.updateSession(this.user.userId)      
      return this.connection.updateChatName(this.user.userId, chatId, newName);
    } 
    else return undefined;
  }


  addUsersToChat(chatId: string, chatName: string, usersIds: string[]) {
    if (this.user)  {
      this.connection.updateSession(this.user.userId)      
      return this.connection.addUsersToChat(this.user.userId, chatId, chatName, usersIds);
    }    
    else return undefined;    
  }

  newChat(chatName: string, usersIds: string[]) {
    if (this.user) {
      this.connection.updateSession(this.user.userId)      
      return this.connection.newChat(this.user, chatName, usersIds);
    } 
    else return undefined;    
  }



  /*
    WEBSOCKET methods
  */

  connectViaWebsocket() {
    if (this.user)
      this.connection.connectViaWS(this.user.userId);
  }
  /*
  tutaj będzie musiał być parsing i obudowanie treści wiadomości 
  w inne informacje jak chat id user id etc. 
  */
  sendMessage(msg: Message) {
    this.connection.sendMessage(msg);
  }

  sendInvitation(inv: Invitation) {
    this.connection.sendInvitation(inv)
  }

  closeWS() {
    this.connection.closeWebSocket();
  }


  updateLogin(newLogin: string) {
    if (this.user) this.user.login = newLogin
  }






















  
  callAngular() {
    this.connection.callAngular();
  }


  updateCookie() {
    if (this.user?.userId)
      this.connection.updateSession(this.user.userId)
  }
  

  createKSID(): string | undefined {
    //this.connection.
    return this.connection.getSessionToken();
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

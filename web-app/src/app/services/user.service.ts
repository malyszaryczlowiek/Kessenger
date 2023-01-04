import { EventEmitter, Injectable } from '@angular/core';
import { map, Observable, of, Subscription } from 'rxjs';
import { ConnectionService } from './connection.service';
// import { v4 as uuidv4 } from 'uuid';
import { Message} from '../models/Message';
import { User } from '../models/User';
import { Router } from '@angular/router';
import { ChatData } from '../models/ChatData';
import { Settings } from '../models/Settings';
import { HttpResponse } from '@angular/common/http';
import { UserSettingsService } from './user-settings.service';
import { Invitation } from '../models/Invitation';
import { Chat } from '../models/Chat';
import { ChatsDataService } from './chats-data.service';
import { MessagePartOff } from '../models/MesssagePartOff';
import { Configuration } from '../models/Configuration';
// import { clearInterval } from 'stompjs';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  user: User | undefined;
  userFetched = false
  chatFetched = false
  fetchingUserDataFinishedEmmiter = new EventEmitter<boolean>() // called during page reload


  logoutTimer: NodeJS.Timeout | undefined;
  logoutSeconds: number = this.settingsService.settings.sessionDuration / 1000    // number of seconds to logout
  logoutSecondsEmitter: EventEmitter<number> = new EventEmitter()

  selectedChatEmitter: EventEmitter<ChatData> = new EventEmitter<ChatData>()
  messageSubscription: Subscription | undefined
  invitationSubscription: Subscription | undefined


  constructor(private connection: ConnectionService, 
    private chats: ChatsDataService,
    private settingsService: UserSettingsService, 
    private router: Router) { 
    console.log('UserService constructor called.')
    

    this.messageSubscription = this.connection.messageEmitter.subscribe(
      (message: MessagePartOff) => {
        console.log(`message from emitter: ${message}`)
        this.chats.insertMessage( message )
        this.dataFetched() 
      },
      (error) => {
        console.log('Error in message emitter: ', error)
        console.log(error)
      },
      () => console.log('on message emitter completed.')
    )


    this.invitationSubscription = this.connection.invitationEmitter.subscribe(
      (invitation: Invitation) => {
        console.log('Got new invitaion', invitation)
      }
    )


    // fetching user data from server
    const userId = this.connection.getUserId();
    if ( userId ) {
      this.updateSessionViaUserId( userId )
      console.log('Session is valid.') 
      // we get user's settings 
      const s = this.connection.user( userId )
      if ( s ) {
        s.subscribe({
          next: (response) => {
            const body = response.body
            if ( body ){
              console.log('UserSerivice.constructor() fetching user login and settings' )
              this.user = body.user
              this.settingsService.setSettings(body.settings)
              this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
              this.restartLogoutTimer()
              this.userFetched = true
              this.dataFetched()
            }
            else {
              // print error message
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
              console.log('UserSerivice.constructor() fetching chats')
              this.chats.initialize( chats )
            }
            this.chatFetched = true
            this.dataFetched()
            this.connectViaWebsocket() // run websocket connection
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
    } else this.router.navigate([''])

  }



  // method called when session expires
  clearService() {
    this.user = undefined;
    this.chats.clear()
    this.settingsService.clearSettings();
    this.connection.disconnect();
    if (this.logoutTimer) clearInterval(this.logoutTimer)
    if (this.messageSubscription) this.messageSubscription.unsubscribe()
    if (this.invitationSubscription) this.invitationSubscription.unsubscribe()
    this.logoutTimer = undefined
    this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
    console.log('UserService clearservice')
  }


  setUserAndSettings(u: User | undefined, s: Settings | undefined) {
    this.user = u;
    if (s) this.settingsService.setSettings(s);
    this.userFetched = true
    this.dataFetched()
  }

  


  restartLogoutTimer() {
    this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
    if (this.logoutTimer) {}
    else {
      this.logoutTimer = setInterval(() => {
        this.logoutSeconds = this.logoutSeconds - 1
        this.logoutSecondsEmitter.emit(this.logoutSeconds)
        if (this.logoutSeconds < 1) {
          console.log('LogoutTimer called!!!')
          clearInterval(this.logoutTimer)
          this.clearService()
          this.router.navigate([''])
        }
      }, 1000)
    }
  }


  updateSession() {
    if (this.user) {
      this.connection.updateSession(this.user.userId);
      this.restartLogoutTimer()
    }
  }

  
  updateSessionViaUserId(userId: string) {
    this.connection.updateSession(userId);
    this.restartLogoutTimer()
  }

  isSessionValid(): boolean {
    return this.connection.isSessionValid();
  }

  dataFetched() {
    this.fetchingUserDataFinishedEmmiter.emit(this.userFetched && this.chatFetched)
  }

  getAllChats() {
    return this.chats.chatAndUsers
  }



  addNewChat(c: ChatData) {
    this.chats.addNewChat(c)
  }



  setChats(chats: ChatData[]) {
    this.chats.initialize( chats )
    this.chatFetched = true
    this.dataFetched()
  }



  changeChat(chatD: ChatData) {
    this.chats.changeChat(chatD)
    this.dataFetched()
  }



  deleteChat(c: ChatData){
    this.chats.deleteChat(c)
    this.dataFetched()
  }

  selectChat(chatId: string | undefined) {
    this.chats.selectChat(chatId)
  }


  insertChatUsers(chatId: string, u: User[]) {
    this.chats.insertChatUsers(chatId, u)
  }


  updateLogin(newLogin: string) {
    if (this.user) this.user.login = newLogin
  }

  markMessagesAsRead(chatId: string) {
    this.chats.markMessagesAsRead(chatId)
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
      this.updateSession()
      return this.connection.user(this.user.userId)
    }
    else 
      return undefined
  }


  changeSettings(s: Settings): Observable<HttpResponse<any>> | undefined {
    if (this.user)  {
      this.updateSession()
      return this.connection.changeSettings(this.user.userId, s)
    }      
    else 
      return undefined
  }


  changeLogin(newLogin: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.changeLogin(this.user.userId, newLogin)
    }
    else
      return undefined
  }



  changePassword(oldPassword: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.changePassword(this.user.userId, oldPassword, newPassword);
    }
    else return undefined;  
  }


  searchUser(search: string): Observable<HttpResponse<User[]>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.searchUser(this.user.userId, search);
    }
    else return undefined;
  }

  // chats


  newChat(chatName: string, usersIds: string[]): Observable<HttpResponse<ChatData[]>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.newChat(this.user, chatName, usersIds);
    } 
    else return undefined;    
  }
  

  getChats(): Observable<HttpResponse<ChatData[]>> | undefined {
    if ( this.user ) {
      this.updateSession()
      return this.connection.getChats(this.user.userId);
    }
    else return undefined;
  }


  getChatUsers(chatId: string): Observable<HttpResponse<User[]>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.getChatUsers(this.user.userId, chatId);
    }
    else return undefined;
  }


  leaveChat(chatId: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.leaveChat(this.user.userId, chatId);
    }
    else return undefined;
  }


  setChatSettings(chat: Chat): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession()
      return this.connection.setChatSettings(this.user.userId, chat);
    } 
    else return undefined;
  }


  addUsersToChat(chatId: string, chatName: string, usersIds: string[]) {
    if (this.user)  {
      this.updateSession()
      return this.connection.addUsersToChat(this.user.userId, chatId, chatName, usersIds);
    }    
    else return undefined;    
  }

 

  /*
    WEBSOCKET methods
  */

  connectViaWebsocket() {
    if (this.user) {
      const conf: Configuration = {
        me: this.user,
        joiningOffset: this.settingsService.settings.joiningOffset,
        chats: this.chats.chatAndUsers.map(
          (c , i, arr) => {
            return {
              chatId: c.chat.chatId,
              partitionOffset: c.partitionOffsets.map(
                (parOff, i, arr2) => {
                  return {
                    partition: parOff.partition,
                    offset: parOff.offset
                  }
                }
              )
            }
          }
        )
      }
      this.connection.connectViaWS( conf );
    }
  }


  sendMessage(msg: Message) {
    this.connection.sendMessage(msg);
  }


  sendInvitation(inv: Invitation) {
    this.connection.sendInvitation(inv)
  }


  






















  
  callAngular() {
    this.connection.callAngular();
  }


  updateCookie() {
    if (this.user)
      this.connection.updateSession(this.user.userId)
  }
  

  createKSID(): string | undefined {
    return this.connection.getSessionToken();
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

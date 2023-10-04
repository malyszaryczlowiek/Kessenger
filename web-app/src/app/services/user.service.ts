import { EventEmitter, Injectable } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
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
import { Configuration } from '../models/Configuration';
import { ChatOffsetUpdate } from '../models/ChatOffsetUpdate';
import { Writing } from '../models/Writing';
import { PartitionOffset } from '../models/PartitionOffset';
import { UserOffsetUpdate } from '../models/UserOffsetUpdate';
import { ResponseNotifierService } from './response-notifier.service';


@Injectable({
  providedIn: 'root'
})
export class UserService {


  user: User | undefined;

  /* codes:
  0. do not update chat list and chatPanel
  1. update both, chat list and chatPanel
  2. update chat list
  3. update chatPanel 
  */
  fetchingUserDataFinishedEmmiter = new EventEmitter<number>() 

  logoutTimer: NodeJS.Timeout | undefined;
  logoutSeconds: number = this.settingsService.settings.sessionDuration / 1000    // number of seconds to logout
  logoutSecondsEmitter: EventEmitter<number>   = new EventEmitter()
  logoutSubscription:   Subscription | undefined

  selectedChatEmitter:  EventEmitter<ChatData> = new EventEmitter<ChatData>()

  newMessagesSubscription:      Subscription | undefined
  oldMessagesSubscription:      Subscription | undefined
  invitationSubscription:       Subscription | undefined

  restartWSSubscription:        Subscription | undefined
  wsConnectionSubscription:     Subscription | undefined
  chatOffsetUpdateSubscription: Subscription | undefined

  reconnectWSTimer:           NodeJS.Timeout | undefined





  constructor(private connection: ConnectionService, 
              private chatsService: ChatsDataService,
              private settingsService: UserSettingsService, 
              private responseNotifier: ResponseNotifierService,
              private router: Router) { 

    console.log('UserService constructor called.')
    this.assignSubscriptions()
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
              this.setUserAndSettings(body.user, body.settings)
              this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
              this.restartLogoutTimer()
              this.chatsService.initialize( body.chatList, body.user.login )
              this.connectViaWebsocket() 
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
    } else this.router.navigate([''])

  }




  assignSubscriptions() {
    if ( ! this.newMessagesSubscription ) {
      this.newMessagesSubscription = this.connection.newMessagesEmitter.subscribe(
        (messageList: Message[]) => {
          let code = this.chatsService.insertNewMessages( messageList )

          // error // tutaj należy wstawić wysyłanie chat offset update, 

          this.dataFetched( code ) 
        },
        (error) => {
          console.log('Error in message emitter: ', error)
        },
        () => console.log('on message emitter completed.')
      )
    }

    if ( ! this.oldMessagesSubscription ) {
      this.oldMessagesSubscription = this.connection.oldMessagesEmitter.subscribe(
        (messageList: Message[]) => {
          console.log(`old messages from emitter: ${messageList}`)
          this.chatsService.insertOldMessages( messageList ) 
        },
        (error) => {
          console.log('Error in message emitter: ', error)
          console.log(error)
        },
        () => console.log('on message emitter completed.')
      ) 
    }

    if (! this.invitationSubscription ) {
      this.invitationSubscription = this.connection.invitationEmitter.subscribe(
        (invitation: Invitation) => {
          const c = this.getChatData( invitation.chatId )
          if ( c ) {
            const cSub = c.subscribe({
              next: (response) => {
                if (response.ok){
                  const body = response.body
                  if ( body ) {
                    const cd: ChatData =  {
                      chat: body.chat,
                      partitionOffsets: invitation.partitionOffsets,
                      messages: new Array<Message>(),
                      unreadMessages: new Array<Message>(),
                      users: new Array<User>(),
                      isNew: true,
                      emitter: new EventEmitter<ChatData>()  
                    }
                    this.addNewChat( cd ) 
                    this.startListeningFromNewChat( cd.chat.chatId, cd.partitionOffsets )
                    this.dataFetched( 2 ) 
  
                    if ( this.user ) {
                      const bodyToSent: UserOffsetUpdate = {
                        userId: this.user.userId,
                        joiningOffset: invitation.myJoiningOffset                    
                      }
                      const u = this.updateJoiningOffset( bodyToSent )
                      if ( u ) {
                        const sub = u.subscribe({
                          next: (response) => {
                            if ( response.ok ) {
                              this.settingsService.settings.joiningOffset = invitation.myJoiningOffset
                              console.log('joining Offset updated ok. to ', invitation.myJoiningOffset)
                            }
                              
                          },
                          error: (err) => {
                            console.log('Error during joining offset update', err)
                          },
                          complete: () => {}
                        })
                      }
                    }                  
                  }                
                }              
              },
              error: (err) => {
                console.error('error in calling getChatData() in invitationSubscription', err) 
              },
              complete: () => {}
            })
          }
        }, 
        (error) => {
          console.error('Got errorn in invitation subscription', error)
        },
        () => {} 
      )
    }


    if (! this.restartWSSubscription ) {
      this.restartWSSubscription = this.connection.restartWSEmitter.subscribe(
        (r: boolean) => {
          // if we get true we need to start timer end trying to reconnect
          if ( r ) {
            if ( ! this.reconnectWSTimer ) {
              console.log('initializing reconnectWSTimer. ')
              this.reconnectWSTimer = setInterval( () => {
                console.log('inside reconnectWSTimer trying to reconnect WS. ')
                this.connectViaWebsocket()
              }, 2000) // we try reconnect every 2s
            }          
          } else {
            // if we get false this means that we need to stop timer,
            // because we connected, 
            // or we disconnected and we do not need to reconnect
            if ( this.reconnectWSTimer ) {
              clearInterval( this.reconnectWSTimer )
              this.reconnectWSTimer = undefined
            }
          }
        }
      )
    }

    if ( ! this.wsConnectionSubscription ) {
      // here we simply notify that all needed data are loaded
      // and WS connection is established 
      // so all fetching sobscribers can load data. 
      this.wsConnectionSubscription = this.connection.wsConnEmitter.subscribe(
        ( bool ) => { 
          if ( this.chatsService.selectedChat ) {
            this.fetchOlderMessages( this.chatsService.selectedChat )
          } else console.error('selected chat is not selected. ')
          this.dataFetched( 1 ) 
        }
      )
    }

    if ( ! this.logoutSubscription ) {
      this.logoutSubscription = this.responseNotifier.logoutEmitter.subscribe(
        (anyy) => {
          this.clearService()
          this.router.navigate(['session-timeout'])
        }
      )
    }

    if ( ! this.chatOffsetUpdateSubscription ) {
      this.chatOffsetUpdateSubscription = this.chatsService.updateChatOffsetEmmiter.subscribe(
        ( update ) => {
          const uid = this.user?.userId
          if ( uid ) {
            const chatOffsetUpdate: ChatOffsetUpdate = {
              userId:           uid,
              chatId:           update.chatId,
              lastMessageTime:  update.lastMessageTime,
              partitionOffsets: update.partitionOffsets 
            } 
            this.connection.sendChatOffsetUpdate( chatOffsetUpdate )
          }          
        }
      )
    }


  }





  // sprawdzić // czy nie trzeba też czegoś restartować 
  // w chat service
  // settings service
  // connection service
  
  clearService() {
    this.user = undefined;
    this.chatsService.clear() 
    this.settingsService.clearSettings();
    if (this.wsConnectionSubscription) {
      this.wsConnectionSubscription.unsubscribe()
      this.wsConnectionSubscription = undefined
    }
    if (this.restartWSSubscription) {
      this.restartWSSubscription.unsubscribe()
      this.restartWSSubscription = undefined
    }
    if (this.reconnectWSTimer) clearInterval(this.reconnectWSTimer)  
    this.connection.disconnect();  
    if (this.logoutTimer) clearInterval(this.logoutTimer)  
    if (this.newMessagesSubscription) {
      this.newMessagesSubscription.unsubscribe()
      this.newMessagesSubscription = undefined
    }
    if (this.oldMessagesSubscription) {
      this.oldMessagesSubscription.unsubscribe()
      this.oldMessagesSubscription = undefined
    }
    if (this.invitationSubscription)  {
      this.invitationSubscription.unsubscribe()
      this.invitationSubscription = undefined
    }
    if ( this.logoutSubscription ) {
      this.logoutSubscription.unsubscribe()
      this.logoutSubscription = undefined
    }
    if ( this.chatOffsetUpdateSubscription ) {
      this.chatOffsetUpdateSubscription.unsubscribe()
      this.chatOffsetUpdateSubscription = undefined
    }
    this.logoutTimer = undefined
    this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
    console.log('UserService clearservice')
  }






  setUserAndSettings(u: User | undefined, s: Settings | undefined) {
    if (u) {
      this.user = u;
      this.connection.setUserId( this.user.userId )
    }
    if (s) this.settingsService.setSettings(s);
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



  updateSession(sendUpdateToServer: boolean) {
    if (this.user) {
      this.connection.updateSession(sendUpdateToServer)
      //this.connection.updateSession(this.user.userId);
      this.restartLogoutTimer()
    }
  }

  


  updateSessionViaUserId(userId: string) {
    this.connection.updateSession(false)
    //this.connection.updateSession(userId);
    this.restartLogoutTimer()
  }




  isSessionValid(): boolean {
    return this.connection.isSessionValid();
  }




  dataFetched(code: number) {
    this.fetchingUserDataFinishedEmmiter.emit( code )
  }




  getAllChats() {
    return this.chatsService.chatAndUsers
  }



  addNewChat(c: ChatData) {
    this.chatsService.addNewChat(c)
  }



  setChats(chats: ChatData[]) {
    if (this.user) {
      this.chatsService.initialize( chats, this.user.userId ) 
    }    
  }



  changeChat(chatD: ChatData) {
    this.chatsService.changeChat(chatD)
    this.dataFetched( 1 )
  }



  deleteChat(c: ChatData){
    this.chatsService.deleteChat(c)
    this.dataFetched( 1 )
  }

  selectChat(chatId: string | undefined) {
    this.chatsService.selectChat(chatId)
  }


  insertChatUsers(chatId: string, u: User[]) {
    this.chatsService.insertChatUsers(chatId, u)
  }


  updateLogin(newLogin: string) {
    if (this.user) this.user.login = newLogin
  }



  // error // zbadać tę metodę czy jest zawsze wywoływana wtedy kiedy trzeba. 
  
  markMessagesAsRead(chatId: string): ChatData | undefined {
    const cd = this.chatsService.markMessagesAsRead(chatId)
    if ( this.user && cd ) {
      if ( cd.num > 0 ) {
        const chatOffsetUpdate: ChatOffsetUpdate = {
          userId:           this.user.userId,
          chatId:           cd.cd.chat.chatId,
          lastMessageTime:  cd.cd.chat.lastMessageTime,
          partitionOffsets: cd.cd.partitionOffsets 
        }    
        this.connection.sendChatOffsetUpdate( chatOffsetUpdate )
      }
      this.fetchingUserDataFinishedEmmiter.emit( 2 ) // update only chat list
      return cd.cd
    } else return undefined   
  }




  getWritingEmmiter() {
    return this.connection.writingEmitter
  }





  // Server calls 



  signUp(log: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    return this.connection.signUp(log, pass);
  }




  signIn(login: string, pass: string): Observable<HttpResponse<{user: User, settings: Settings, chatList: Array<ChatData>}>> | undefined {
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



  // unused
  getUser(): Observable<HttpResponse<{user: User, settings: Settings}>> | undefined {
    if (this.user){ 
      this.updateSession(false)
      return this.connection.user(this.user.userId)
    }
    else 
      return undefined
  }



  updateJoiningOffset(body: UserOffsetUpdate) {
    if (this.user){ 
      this.updateSession(false)
      return this.connection.updateJoiningOffset(this.user.userId, body)
    }
    else 
      return undefined
  }



  changeSettings(s: Settings): Observable<HttpResponse<any>> | undefined {
    if (this.user)  {
      this.updateSession(false)
      return this.connection.changeSettings(this.user.userId, s)
    }      
    else 
      return undefined
  }



  changeLogin(newLogin: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.changeLogin(this.user.userId, newLogin)
    }
    else
      return undefined
  }



  changePassword(oldPassword: string, newPassword: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.changePassword(this.user.userId, oldPassword, newPassword);
    }
    else return undefined;  
  }


  searchUser(search: string): Observable<HttpResponse<User[]>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.searchUser(this.user.userId, search);
    }
    else return undefined;
  }

  // chats


  newChat(chatName: string, usersIds: string[]): Observable<HttpResponse<ChatData[]>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.newChat(this.user, chatName, usersIds);
    } 
    else return undefined;    
  }
  

  getChats(): Observable<HttpResponse<ChatData[]>> | undefined {
    if ( this.user ) {
      this.updateSession(false)
      return this.connection.getChats(this.user.userId);
    }
    else return undefined;
  }

  getChatData(chatId: string): Observable<HttpResponse<{chat: Chat, partitionOffsets: Array<{partition: number, offset: number}>}>> | undefined  {
    if (this.user) {
      this.updateSession(false)
      return this.connection.getChatData(this.user.userId, chatId);
    }
    else return undefined;
  }


  getChatUsers(chatId: string): Observable<HttpResponse<User[]>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.getChatUsers(this.user.userId, chatId);
    }
    else return undefined;
  }


  leaveChat(chatId: string): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.leaveChat(this.user.userId, chatId);
    }
    else return undefined;
  }


  setChatSettings(chat: Chat): Observable<HttpResponse<any>> | undefined {
    if (this.user) {
      this.updateSession(false)
      return this.connection.setChatSettings(this.user.userId, chat);
    } 
    else return undefined;
  }

  
  addUsersToChat(chatId: string, chatName: string, usersIds: string[], partitionOffsets: PartitionOffset[]) {
    if (this.user)  {
      this.updateSession(false)
      return this.connection.addUsersToChat(this.user.userId, this.user.login, chatId, chatName, usersIds, partitionOffsets);
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
        chats: this.chatsService.chatAndUsers.map(
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
    if (this.user) {
      const body = {
        user:    this.user,
        message: msg
      }
      this.connection.sendMessage( body );
    }    
  }


  sendInvitation(inv: Invitation) {
    this.connection.sendInvitation(inv)
  }



  sendWriting(w: Writing){
    this.connection.sendWriting( w )
  }



  sendChatOffsetUpdate(update: ChatOffsetUpdate) {
    this.connection.sendChatOffsetUpdate( update )
  }



  startListeningFromNewChat(chatId: string, partitionOffsets: PartitionOffset[]) {
    this.connection.startListeningFromNewChat( chatId , partitionOffsets)
  }



  fetchOlderMessages(chatId: string) {
    this.connection.fetchOlderMessages( chatId )
  }
  


  isWSconnected(): boolean {
    return this.connection.isWSconnected()
  }



  isWSconnectionDefined(): boolean {
    return this.connection.isWSconnectionDefined()
  }



  


}

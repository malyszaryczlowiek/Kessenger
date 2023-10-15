import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, share, startWith, Subject, Subscription, switchMap } from 'rxjs';
// services
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
import { UserService } from 'src/app/services/user.service';
// models
import { Chat } from 'src/app/models/Chat';
import { ChatData } from 'src/app/models/ChatData';
import { User } from 'src/app/models/User';
import { ConnectionService } from 'src/app/services/connection.service';
import { ChatsDataService } from 'src/app/services/chats-data.service';




@Component({
  selector: 'app-edit-chat-settings',
  templateUrl: './edit-chat-settings.component.html',
  styleUrls: ['./edit-chat-settings.component.css']
})
export class EditChatSettingsComponent implements OnInit, OnDestroy {



  chatSettings = new FormGroup({
    newChatName: new FormControl(''),
    silent: new FormControl(false) 
  })




  searchUserForm = new FormGroup({
    login: new FormControl('',[Validators.required, Validators.minLength(4)])
  })


  //responseMessage: any | undefined
  // fetchingSubscription:  Subscription | undefined;



  chatData:                  ChatData | undefined;
  foundUsers                  = new Array<User>();
  selectedUsers               = new Array<User>();
  searchTerm: Subject<string> = new Subject();

  fetchingUserEmmiter:  EventEmitter<any> = new  EventEmitter<any>() 
  // fetchingUserSubscription: Subscription | undefined
  fetchingDataSubscription: Subscription | undefined



  
  constructor( private connectionService: ConnectionService,
               private chatService: ChatsDataService,
               private router: Router, 
               private activated: ActivatedRoute,
               private responseNotifier: ResponseNotifierService,
               // private userService: UserService
               ) { }
  




  ngOnInit(): void {


    //this.fetchingDataSubscription = this.connectionService.detaFetchedEmitter.subscribe(
    this.fetchingDataSubscription = this.chatService.updateChatPanelEmmiter.subscribe(
      (n) => {
        this.fetchData()
      }
    )





    /* this.fetchingUserSubscription = this.fetchingUserEmmiter.subscribe(
      () => {
        if ( this.chatData?.chat.groupChat || this.chatData?.users.length == 0 ) {
          const c = this.userService.getChatUsers(this.chatData.chat.chatId)
          if ( c ) {
            c.subscribe({
              next: (response) => {
                const body = response.body
                if (response.ok && body && this.chatData) {
                  console.log('inserting users to chat.')
                  console.log(body)
                  this.userService.insertChatUsers(this.chatData.chat.chatId, body)
                  this.chatData.users = body
                }
                if (response.ok && ! body) {
                  console.log(response.ok, body)
                }
              },
              error: (err) => {
                this.responseNotifier.handleError( err )
              },
              complete: () => {},
            })
          } else {
            this.router.navigate(['session-timeout']) 
          } 
        }
      }
    )
 */


/*     this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if (b) {
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) {
            this.chatData = this.userService.getAllChats().find((chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            });
            if (this.chatData) {
              this.chatSettings.controls.silent.setValue(this.chatData.chat.silent)
              this.fetchingUserEmmiter.emit()
            } else {
              this.router.navigate(['page-not-found']) 
            }
          } 
        }
      }
    )
    */    
   
   
    // if ( this.userService.isWSconnected() ) this.userService.dataFetched( 1 )
    if ( this.connectionService.isWSconnected() ) this.fetchData()
  }


  
  ngOnDestroy(): void {
    // if (this.fetchingSubscription) this.fetchingSubscription.unsubscribe()
    // if (this.fetchingUserSubscription) this.fetchingUserSubscription.unsubscribe()
    if (this.fetchingDataSubscription) this.fetchingDataSubscription.unsubscribe()
    console.log('EditChatSettingsComponent.ngOnDelete() called.')
  }



  

  private fetchData() {
    const chatId = this.activated.snapshot.paramMap.get('chatId');
    if ( chatId ) {
      /*  .getAllChats().find((chatData, index, arr) => {
        return chatData.chat.chatId == chatId;
      }); */
      this.chatData = this.chatService.getCurrentChatData()
      if (this.chatData) {
        this.chatSettings.controls.silent.setValue(this.chatData.chat.silent)
        // this.fetchingUserEmmiter.emit()                     //     to zakomentowałem 
        if ( this.chatData.chat.groupChat ) { //  tutaj był jeszcze warunek   || this.chatData?.users.length == 0
          const c = this.connectionService.getChatUsers(this.chatData.chat.chatId)
          if ( c ) {
            c.subscribe({
              next: (response) => {
                const body = response.body
                if (response.ok && body && this.chatData) {
                  console.log('inserting users to chat.')
                  console.log(body)
                  this.chatService.insertChatUsers(this.chatData.chat.chatId, body)
                  // this.userService.insertChatUsers(this.chatData.chat.chatId, body)
                  this.chatData.users = body
                }
                if (response.ok && ! body) {
                  console.log(response.ok, body)
                }
              },
              error: (err) => {
                this.responseNotifier.handleError( err )
              },
              complete: () => {},
            })
          } else {
            this.router.navigate(['session-timeout']) 
          } 
        }

      } else {
        this.router.navigate(['page-not-found']) 
      }
    }
  }

  


  // here we save changed name or silence
  saveChanges() {
    if (this.chatData){
      let body: Chat = this.chatData.chat
      const newName  = this.chatSettings.value.newChatName
      let newSilent: boolean = false 
      if (this.chatSettings.value.silent) newSilent = true
      if (newName) {
        body = {
          chatId:          this.chatData.chat.chatId,
          chatName:        newName,
          groupChat:       this.chatData.chat.groupChat,
          lastMessageTime: this.chatData.chat.lastMessageTime,
          silent:          newSilent
        }
      } else {
        body = {
          chatId:          this.chatData.chat.chatId,
          chatName:        this.chatData.chat.chatName,
          groupChat:       this.chatData.chat.groupChat,
          lastMessageTime: this.chatData.chat.lastMessageTime,
          silent:          newSilent
        }
      }
      // const c = this.userService.setChatSettings( body )
      const c = this.connectionService.setChatSettings( body )
      if ( c ) {
        c.subscribe({
          next: (response) => {
            if (response.ok) {
              if (this.chatData) {
                
                const newChatData: ChatData = {
                  chat: body,
                  messages: this.chatData.messages,
                  partitionOffsets:  this.chatData.partitionOffsets,
                  users:  this.chatData.users,
                  unreadMessages: this.chatData.unreadMessages,
                  emitter: this.chatData.emitter                  
                }
                // this.userService.changeChat(newChatData)
                // trzeba zmienić czat w chatService
                this.chatService.changeChat( newChatData )
                const printBody = {
                  header: 'Update',
                  //code: 0,
                  message: `${response.body.message}`
                }
                this.responseNotifier.printNotification( printBody ) 
              }
            } else {
              console.log('Changing chat settings has gone wrong')
            }
          },
          error: (err) => {
            console.warn(err)
            this.responseNotifier.handleError( err )            
          },
          complete: () => {},
        })
      }   
    }
  }





  // if we do not want change data we can navigate back to chat side
  onCancel() {
    if (this.chatData){
      // this.userService.updateSession(true)
      this.connectionService.updateSession()
      this.router.navigate(['user', 'chat', `${this.chatData.chat.chatId}`]);
    } else {
      this.router.navigate(['user']);
    }    
  }





  backToChat() {
    if (this.chatData){
      // this.userService.updateSession(true)
      this.connectionService.updateSession()
      this.router.navigate(['user', 'chat', `${this.chatData.chat.chatId}`]);
    } else {
      this.router.navigate(['user']);
    }    
  }





  // here we handle request to leave chat. 
  leaveChat() {
    console.log('onDelete was called.')
    const cid = this.chatData?.chat.chatId
    if ( cid )  {
      // const c = this.userService.leaveChat(cid)
      const c = this.connectionService.leaveChat( cid )
      if ( c ) {
        c.subscribe({
          next: (response) => {
            if (response.ok) {
              if (this.chatData)
                // this.userService.deleteChat(this.chatData)
                this.chatService.deleteChat( this.chatData )
                this.router.navigate(['user'])
            }
          },
          error: (err) => {
            console.warn(err)
            this.responseNotifier.handleError( err )
          },
          complete: () => {},
        }) 
      } else {
        console.log('Session is out.')
        // this.userService.clearService()
        this.connectionService.disconnect()
        this.router.navigate(['session-timeout'])
      }
    } else {
      console.log('chatId not defined.')
    }    
  }





  addUsers() {
    if (this.chatData) {
      /* const c2 = this.userService.addUsersToChat(
        this.chatData.chat.chatId, this.chatData.chat.chatName, 
        this.selectedUsers.map(u => u.userId), this.chatData.partitionOffsets
      ) */
      const c = this.connectionService.addUsersToChat(
        this.chatData.chat.chatId,
        this.chatData.chat.chatName, 
        this.selectedUsers.map(u => u.userId), this.chatData.partitionOffsets) 
      if ( c ) {
        c.subscribe({
          next: (response) => {
            const body = {
              header: 'Update',
              // code: 0,
              message: `${response.body}`
            }
            this.responseNotifier.printNotification( body ) 
          },
          error: (err) => {
            console.warn(err)
            this.responseNotifier.handleError( err )
          },
          complete: () => {}
        })
      } else 
        this.router.navigate(['session-timeout'])
    }
  }





  searchUser() {
    this.foundUsers = new Array<User>()
    this.foundUsers.find
    const searchLogin = this.searchUserForm.controls.login.value
    if ( this.searchUserForm.controls.login.valid && searchLogin ) {
      const s = this.searchTerm.pipe(
        startWith( searchLogin ),
        debounceTime(900),
        distinctUntilChanged(),
        switchMap( (login) => {
          // const c = this.userService.searchUser(login)
          const c = this.connectionService.searchUser( login )
          console.log('search login key pressed. Login:  '+ login)
          if (c) return c
          else return of()
        }),
        share()
      )
      if ( s ) {
        console.log('returned observable is valid')
        s.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const users = response.body
              if ( users ) {
                console.log('users in chat', this.chatData?.users)
                // this.userService.updateSession(true)
                this.connectionService.updateSession()
                this.foundUsers = users.filter(
                  (user,i,arr) => {
                    const alreadySelected = this.selectedUsers.filter( (u, index,arr) => {
                      return u.userId == user.userId 
                    })
                    const otherThanMe = this.chatService.user?.userId != user.userId
                    const alreadyInChat = this.chatData?.users.filter((u,i,arr) => {
                      return u.login == user.login
                    })
                    console.log(alreadySelected?.length == 0 && otherThanMe && alreadyInChat?.length == 0)
                    return alreadySelected?.length == 0 && otherThanMe && alreadyInChat?.length == 0
                  }
                )
              }
            }
            if (response.status == 204){
              this.foundUsers = new Array<User>(); 
              console.log('No User found')
            }
          },
          error: (err) => {
            this.foundUsers = new Array()  
            console.warn(err)
            this.responseNotifier.handleError( err )
          },
          complete: () => {}
        })
      } else {
        this.router.navigate(['session-timeout'])
      }
    } else {
      console.log('Search User form is not valid')
    }
  }





  addToSelected(u: User) {
    this.selectedUsers.push(u);
    this.foundUsers = this.foundUsers.filter( (user, index, array) => {
      return user.userId != u.userId;
    });
  }





  unselect(u: User) {
    this.selectedUsers = this.selectedUsers.filter((user, index, array) => {
      return u.userId != user.userId
    })
    this.foundUsers.push(u);
  }




}

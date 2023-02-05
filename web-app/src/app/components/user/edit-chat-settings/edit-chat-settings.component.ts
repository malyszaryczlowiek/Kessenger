import { Component, EventEmitter, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, share, startWith, Subject, Subscription, switchMap } from 'rxjs';
import { Chat } from 'src/app/models/Chat';
import { ChatData } from 'src/app/models/ChatData';
import { User } from 'src/app/models/User';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-edit-chat-settings',
  templateUrl: './edit-chat-settings.component.html',
  styleUrls: ['./edit-chat-settings.component.css']
})
export class EditChatSettingsComponent implements OnInit {

  chatSettings = new FormGroup({
    newChatName: new FormControl(''),
    silent: new FormControl(false) 
  })

  searchUserForm = new FormGroup({
    login: new FormControl('',[Validators.required, Validators.minLength(4)])
  })

  chatData?: ChatData;
  responseMessage: any | undefined
  fetchingSubscription: Subscription | undefined
  foundUsers = new Array<User>()
  selectedUsers = new Array<User>();
  searchTerm: Subject<string> = new Subject()

  fetchingUserEmmiter:  EventEmitter<any> = new  EventEmitter<any>() 
  fetchingUserSubscription: Subscription | undefined


  
  constructor(
    private router: Router, 
    private activated: ActivatedRoute,
    private userService: UserService) { }





  ngOnInit(): void {

    this.fetchingUserSubscription = this.fetchingUserEmmiter.subscribe(
      () => {
        if ( this.chatData ) {
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
                console.log("ERROR", err)
                if (err.status == 401){
                  console.log('Session is out.')
                  this.userService.clearService()
                  this.router.navigate(['session-timeout'])
                }
                else {
                  this.responseMessage = err.error
                }
              },
              complete: () => {
                
              },
            })
          } else {
            this.router.navigate(['session-timeout']) 
          } 
        }
      }
    )

    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
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
    if ( this.userService.isWSconnected() ) this.userService.dataFetched( 1 )
  }

  ngOnDelete() {
    if (this.fetchingSubscription) this.fetchingSubscription.unsubscribe()
    if (this.fetchingUserSubscription) this.fetchingUserSubscription.unsubscribe()
    console.log('EditChatSettingsComponent.ngOnDelete() called.')
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
      const c = this.userService.setChatSettings(body)
      if ( c ) {
        c.subscribe({
          next: (response) => {
            if (response.ok) {
              if (this.chatData) {
                this.responseMessage = response.body.message
                const newChatData: ChatData = {
                  chat: body,
                  messages: this.chatData.messages,
                  partitionOffsets:  this.chatData.partitionOffsets,
                  users:  this.chatData.users,
                  unreadMessages: this.chatData.unreadMessages,
                  emitter: this.chatData.emitter                  
                }
                this.userService.changeChat(newChatData)
              }
            } else {
              console.log('Changing chat settings has gone wrong')
            }
          },
          error: (err) => {
            console.log(err)
            this.responseMessage = err.error            
          },
          complete: () => {},
        })
      }   
    }
  }



  // if we do not want change data we can navigate back to chat side
  onCancel() {
    if (this.chatData){
      this.userService.updateSession()
      this.router.navigate(['user', 'chat', `${this.chatData.chat.chatId}`]);
    } else {
      this.router.navigate(['user']);
    }    
  }



  backToChat() {
    if (this.chatData){
      this.userService.updateSession()
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
      const c = this.userService.leaveChat(cid)
      if ( c ) {
        c.subscribe({
          next: (response) => {
            if (response.ok) {
              if (this.chatData)
                this.userService.deleteChat(this.chatData)
                this.router.navigate(['user'])
            }
          },
          error: (err) => {
            console.log("ERROR", err)
            if (err.status == 401){
              console.log('Session is out.')
              this.userService.clearService()
              this.router.navigate(['session-timeout'])
            }
            else {
              this.responseMessage = err.error
            }
          },
          complete: () => {},
        }) 
      } else {
        console.log('Session is out.')
        this.userService.clearService()
        this.router.navigate(['session-timeout'])
      }
    } else {
      console.log('chatId not defined.')
    }    
  }


  
  clearNotification() {
    this.userService.updateSession()
    this.responseMessage = undefined
  }




  addUsers() {
    if (this.chatData) {
      const c = this.userService.addUsersToChat(
        this.chatData.chat.chatId, this.chatData.chat.chatName, 
        this.selectedUsers.map(u => u.userId), this.chatData.partitionOffsets
        )
      if ( c ) {
        c.subscribe({
          next: (response) => {
            console.log(response.body)
            this.responseMessage = response.body
          },
          error: (err) => {
            console.log("ERROR", err)
            if (err.status == 401){
              console.log('Session is out.')
              this.userService.clearService()
              this.router.navigate(['session-timeout'])
            }
            else {
              this.responseMessage = err.error
            }
          },
          complete: () => {}
        })
      } else 
        this.router.navigate(['session-timeout'])
    }
  }


  searchUser() {
    this.userService.updateSession()
    this.foundUsers = new Array<User>()
    this.foundUsers.find
    const searchLogin = this.searchUserForm.controls.login.value
    if ( this.searchUserForm.controls.login.valid && searchLogin ) {
      const s = this.searchTerm.pipe(
        startWith( searchLogin ),
        debounceTime(900),
        distinctUntilChanged(),
        switchMap( (login) => {
          const c = this.userService.searchUser(login)
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
                this.foundUsers = users.filter(
                  (user,i,arr) => {
                    const alreadySelected = this.selectedUsers.filter( (u, index,arr) => {
                      return u.userId == user.userId 
                    })
                    const otherThanMe = this.userService.user?.userId != user.userId
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
          error: (error) => {
            console.log("ERROR", error)
            this.foundUsers = new Array()  
            if (error.status == 401){
              console.log('Session is out.')
              this.router.navigate(['session-timeout'])
            }
            else {
              this.responseMessage = error.error
            }
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

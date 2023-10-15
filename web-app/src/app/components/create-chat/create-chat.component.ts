import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, share, startWith, Subject, switchMap } from 'rxjs';
//  services
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { ConnectionService } from 'src/app/services/connection.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
// models
import { ChatData } from 'src/app/models/ChatData';
import { Message } from 'src/app/models/Message';
import { User } from 'src/app/models/User';



@Component({
  selector: 'app-create-chat',
  templateUrl: './create-chat.component.html',
  styleUrls: ['./create-chat.component.css']
})
export class CreateChatComponent implements OnInit, OnDestroy {

  

  searchUserForm = new FormGroup({
    login: new FormControl('',[Validators.required, Validators.minLength(4)])
  });
  foundUsers: User[] = new Array<User>();
  searchTerm: Subject<string> = new Subject()

  
  public selectedUsers = new Array<User>();

  chatForm = new FormGroup({
    chatName: new FormControl('',[Validators.required, Validators.minLength(3)])
    //groupChat: new FormControl(false),
  });

  public disableSubmitting: boolean = true;
  
  // returnedError: any | undefined
  createMessage: string | undefined





  constructor(
    private connectionService: ConnectionService,
    private chatService: ChatsDataService,
    private responseNotifier: ResponseNotifierService,
    private router: Router) { }





  ngOnInit(): void {
    console.log('CreateChatComponent.ngOnInit()')
    this.connectionService.updateSession()
    // this.userService.updateSession(true)  
  }





  ngOnDestroy(): void {
    console.log('CreateChatComponent.ngOnDestroy()')
  }




  create() {
    const chatName = this.chatForm.controls.chatName.value
    // const me = this.userService.user
    const me = this.chatService.user
    if ( chatName && this.selectedUsers.length >= 1 && me) {
      const c = this.connectionService.newChat(me, chatName, this.selectedUsers.map(u => u.userId))
      // const c = this.userService.newChat(chatName, this.selectedUsers.map(u => u.userId))
      if ( c ) {
        this.createMessage = 'Creating Chat... Wait a few seconds.'
        c.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const body = response.body?.at(0)
              const me = this.chatService.user
              // const me = this.userService.user
              if ( body && me ) {
                this.selectedUsers.push( me )
                const chatData: ChatData = {
                  chat: body.chat,
                  users: this.selectedUsers,                   
                  partitionOffsets: body.partitionOffsets,
                  messages: new Array<Message>(),
                  unreadMessages: new Array<Message>(),
                  emitter: new EventEmitter<ChatData>()
                }
                // sending to server information to listen messages from this chat.
                this.chatService.addNewChat( chatData ) 
                this.connectionService.startListeningFromNewChat(chatData.chat.chatId, chatData.partitionOffsets)
                
                // old
                //this.userService.startListeningFromNewChat( chatData.chat.chatId, chatData.partitionOffsets )
                // this.userService.addNewChat( chatData ) 

                // inform chat created
                this.createMessage = 'Chat created, Redirecting to it.'    
                
                // and we set redirection
                setTimeout(() => {
                  this.createMessage = undefined
                  this.router.navigate(['user', 'chat', `${body.chat.chatId}`])
                }, 1500 )
              }
            } else {
              console.log('Chat creation status != 200')
            }
          }, 
          error: (err) => {
            this.createMessage = undefined
            console.log("ERROR", err)
            const print = {
              header: 'Error',
              code: err.error.num,
              message: err.error.message
            }
            this.responseNotifier.printNotification( print )            
            if (err.status == 401){
              console.log('Session is out.')
              this.router.navigate(['session-timeout'])
            }
          },
          complete: () => {}
        })
      } else {
        this.router.navigate(['session-timeout'])
      }
    }
  }





  search() {
    this.connectionService.updateSession()
    this.foundUsers = new Array<User>()
    this.foundUsers.find
    const searchLogin = this.searchUserForm.controls.login.value
    const myId = this.chatService.user?.userId
    if ( this.searchUserForm.controls.login.valid && searchLogin && myId ) {
      const s = this.searchTerm.pipe(
        startWith( searchLogin ),
        debounceTime(900),
        distinctUntilChanged(),
        switchMap( (login) => {
          const c = this.connectionService.searchUser( login)
          // const c = this.userService.searchUser(login)
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
                this.foundUsers = new Array<User>(); 
                users.forEach((user, index, array) => {
                  const exists = this.selectedUsers.find( (u, index,arr) => {
                    return u.userId == user.userId 
                  })
                  if (!exists && myId != user.userId) {
                    this.foundUsers.push( user )
                  }
                })
              }
            }
            if (response.status == 204){
              this.foundUsers = new Array<User>(); 
              console.log('No User found')
            }
          },
          error: (err) => {
            console.log("ERROR", err)
            this.foundUsers = new Array()  // todo to skasować CHYBA można
            const print = {
              header: 'Error',
              code: err.error.num,
              message: err.error.message
            }
            this.responseNotifier.printNotification( print )

            if (err.status == 401){
              console.log('Session is out.')
              this.router.navigate(['session-timeout'])
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
    this.validateForm();
  }

  
  

  
  unselect(u: User) {
    this.selectedUsers = this.selectedUsers.filter((user, index, array) => {
      return u.userId != user.userId
    })
    this.foundUsers.push(u);
    this.validateForm();
  }





  validateForm() {
    this.disableSubmitting = !( this.chatForm.valid && this.selectedUsers.length > 0 );
    this.connectionService.updateSession()
    // this.userService.updateSession(true)
  }



  /* clearError() {
    this.userService.updateSession(true)
    this.returnedError = undefined
  } */


}

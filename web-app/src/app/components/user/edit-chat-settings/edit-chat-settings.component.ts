import { Component, OnInit } from '@angular/core';
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
  });
  searchUserForm = new FormGroup({
    login: new FormControl('',[Validators.required, Validators.minLength(4)])
  });
  chatData?: ChatData;
  responseMessage: any | undefined
  fetchingSubscription: Subscription | undefined
  foundUsers = new Array<User>()
  selectedUsers = new Array<User>();
  searchTerm: Subject<string> = new Subject()



  
  constructor(
    private router: Router, 
    private activated: ActivatedRoute,
    private userService: UserService) { }


    // tutaj 
    /*
    tutaj trzeba zimplementować:
    1. pobieranie użytkowników tak aby w chatdata była ich lista 
       
       taka lista może być wykorzysana jako dane do szukania 
       nowych użytkowników do czatu. 

    2. dodawanie nowych użytkowników do chatu jeśli jest on grupowy
       
       skopiować wyszukiwanie użytkownika z create chat. 

    */


  ngOnInit(): void {


        /* const c = this.userService.getChatUsers(cd.chat.chatId)
    if ( c ) {
      c.subscribe({
        next: (response) => {
          const body = response.body
          if (response.ok && body) {
            console.log('inserting users to chat.')
            this.userService.insertChatUsers(cd, body)
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
            this.errorBody = err.error
          }
        },
        complete: () => {
          // this.userService.dataFetched()
        },
      })
    } else {
      this.router.navigate(['session-timeout']) // may cause troubles?
    } */




    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if (b) {
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) {
            this.chatData = this.userService.chatAndUsers.find((chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            });
            if (this.chatData) {
              this.chatSettings.controls.silent.setValue(this.chatData.chat.silent)
            } 
          } 
        }
      }
    )
    this.userService.dataFetched()
  }

  ngOnDelete() {
    if (this.fetchingSubscription) this.fetchingSubscription.unsubscribe
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

  todo
  1. zaimplementować pobieranie listy użytkowników czatu
  2. przy wyszukiwaniu użytkownika, którego będziemy chcieli dodać trzeba
     odjąć tych użytkowników którzy są już w czacie
  3. zaimplementować dodawanie użytkowników do czatu.   


  addUsers() {
    here
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
                this.foundUsers = new Array<User>(); 
                users.forEach((user, index, array) => {
                  const exists = this.selectedUsers.find( (u, index,arr) => {
                    return u.userId == user.userId 
                  })
                  if (!exists && this.userService.user?.userId != user.userId) {
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
          error: (error) => {
            console.log("ERROR", error)
            this.foundUsers = new Array()  // todo to skasować CHYBA można
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
    // this.validateForm();
  }

  unselect(u: User) {
    this.selectedUsers = this.selectedUsers.filter((user, index, array) => {
      return u.userId != user.userId
    })
    this.foundUsers.push(u);
    this.validateForm();
  }

  validateForm() {
    // this.disableSubmitting = !( this.chatForm.valid && this.selectedUsers.length > 0 );
    this.userService.updateSession()
  }


}

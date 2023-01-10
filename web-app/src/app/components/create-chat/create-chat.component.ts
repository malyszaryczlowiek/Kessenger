import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, share, startWith, Subject, switchMap } from 'rxjs';
import { ChatData } from 'src/app/models/ChatData';
import { Message } from 'src/app/models/Message';
import { MessagePartOff } from 'src/app/models/MesssagePartOff';
import { User } from 'src/app/models/User';
import { UserService } from 'src/app/services/user.service';

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
  
  returnedError: any | undefined
  createMessage: string | undefined





  constructor(private userService: UserService, private router: Router) { }


  ngOnInit(): void {
    console.log('CreateChatComponent.ngOnInit()')
    this.userService.updateSession()  
  }

  ngOnDestroy(): void {
    console.log('CreateChatComponent.ngOnDestroy()')
  }

  todo
  /*

  wysyłanie rzeczy przez websocket

  1. wysyłąnie offsetu chatu gdy: 
     a) (DONE) jesteśmy w czacie i odbieramy wiadomość
     b) (DONE) klikamy w dany czat na liście i nas tam przeności,
        ale lista nowych wiadomości nie jest pusta

  2. wysyłanie informacji o nowym czacie żeby utworzyć odpowiedni consumer. 
     a) (DONE) jak tworzymy nowy czat i uzyskujemy odpowiedź
     b) (DONE) jak dostajemy invitation i jesteśmy po pobraniu 
        wszystkich danych o tym czacie z odpowiedniego (normalnego) endpointa. 
        - (DONE) raz powinniśmy zmienić na liście, że jest 'NEW'

    
  3. odbieranie zaproszenia        
     patrz pkt 2a)

  4. sprawdzić przetwarzanie przychodzących wiadomosci, 
     czy są prawidłowo przetwarzane.    

  5. sprawdzić czy czat istnieje tylko wtedy jak istnieje topic tego czatu
      - a jak topic tego czatu istnieje, to czy writing tego czatu istnieje, 
        jeśli nie to należy jeszcze raz spróbować utworzyć taki topic. 


  6. zrobić fetchowanie wcześniejszych wiadomości.       
     zrobić to tak, że tworzonuy jest tylko tymczasowy consumer, 
     któ®y pobierze dane z założonego zakresu i zaraz zostanie zamknięty. 
       

    wypracować mechanizm co w przypadku, gdy przypisujemy
    do consumera czaty dany chat nie istnieje i wywala error
    to należy wtedy wysłać wiadomość, że chcemy utworzyć nowy topic/chat 
    i PO UTWORZENIU NOWEGO CHATU / TOPICA 
    sprawdzić czy Future jest już zakończony i zrestartować go ewentualnie.
  */


  create() {
    const chatName = this.chatForm.controls.chatName.value
    const me = this.userService.user
    if ( chatName && this.selectedUsers.length >= 1 && me) {
      const c = this.userService.newChat(chatName, this.selectedUsers.map(u => u.userId))
      if ( c ) {
        this.createMessage = 'Creating Chat... Wait a few seconds.'
        c.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const body = response.body?.at(0)
              const me = this.userService.user
              if ( body && me ) {
                this.selectedUsers.push( me )
                const chatData: ChatData = {
                  chat: body.chat,
                  users: this.selectedUsers,                   
                  partitionOffsets: body.partitionOffsets,
                  messages: new Array<Message>(),
                  unreadMessages: new Array<MessagePartOff>(),
                  emitter: new EventEmitter<ChatData>
                }
                // sending to server information to listen messages from this chat.
                this.userService.startListeningFromNewChat( chatData.chat.chatId )
                this.userService.addNewChat( chatData ) 
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
          error: (error) => {
            this.createMessage = undefined
            console.log("ERROR", error)
            if (error.status == 401){
              console.log('Session is out.')
              this.router.navigate(['session-timeout'])
            }
            /* if (error.status == 400) {
              if () {

              }
            } */
            else {
              this.returnedError = error.error
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
              this.returnedError = error.error
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
    this.userService.updateSession()
  }

  clearError() {
    this.userService.updateSession()
    this.returnedError = undefined
  }


}

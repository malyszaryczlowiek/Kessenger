import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounce, debounceTime, distinctUntilChanged, of, share, startWith, Subject, switchMap } from 'rxjs';
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


  /*
[
    {
      login: "log1",
      userId: "uuid1"
    },
    {
      login: "log2",
      userId: "uuid2"
    }
  ];  
  */
  
  public selectedUsers = new Array<User>();

  chatForm = new FormGroup({
    chatName: new FormControl('',[Validators.required, Validators.minLength(3)])
    //groupChat: new FormControl(false),
  });

  public disableSubmitting: boolean = true;
  
  returnedError: any | undefined







  constructor(private userService: UserService, private router: Router) { }


  ngOnInit(): void {
    console.log('CreateChatComponent.ngOnInit()')
    this.userService.updateSession()  
  }

  ngOnDestroy(): void {
    console.log('CreateChatComponent.ngOnDestroy()')
  }


  // to jest po naciśnięciu buttonu submit
  // tutaj wysyłamy rządanie utworzenia czatu
  // jeśli w odpowiedzi dostaniwmy dane z chatem to 
  // w userService należy zaktualizować listę czatów
  create() {
    const chatName = this.chatForm.controls.chatName.value
    const me = this.userService.user
    if ( chatName && this.selectedUsers.length >= 1 && me) {
      this.selectedUsers.push(me)
      const c = this.userService.newChat(chatName, this.selectedUsers.map(u => u.userId))
      if ( c ) {
        c.subscribe({
          next: (response) => {



            // tutaj teraz impelementować
            // todo implement this



          }, 
          error: (e) => {},
          complete: () => {}
        })
      } else {
        this.router.navigate(['session-timeout'])
      }
    }
  }





  // metoda do szukania użytkownika w bazie danych 
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
                  if (! exists) {
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
  



  // https://www.youtube.com/shorts/Z6ZQc_Fm42k
  
  


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


}

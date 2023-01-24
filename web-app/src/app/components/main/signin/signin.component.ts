import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-signin',
  templateUrl: './signin.component.html',
  styleUrls: ['./signin.component.css']
})
export class SigninComponent implements OnInit {
 
  signInForm = new FormGroup({
    login: new FormControl('', [Validators.required, Validators.minLength(4)]),
    password: new FormControl('', [Validators.required, Validators.minLength(6)])  //  todo dodać walidatory
  });
  
  
  returnedError: any | undefined;

  constructor(private userService: UserService, private router: Router) { }

  ngOnInit(): void {
  }

  onSubmit() {
    const login = this.signInForm.value.login;
    const pass = this.signInForm.value.password;
    if (login && pass ) {
      const signin = this.userService.signIn(login, pass)
      if ( signin ){
        signin.subscribe({
          next: (response) => {
            if (response.status === 200) {

              const body = response.body
              if ( body ) {
                this.userService.setUserAndSettings(
                  body.user,
                  body.settings
                );
                // after successfull request we should update KSID cookie 
                // to have correct userId
                this.userService.updateSession()
                this.userService.setChats( response.body.chatList )
                this.userService.connectViaWebsocket()
                this.router.navigate(['user']);
              }              
            } else {
              console.log('sign in status other than 200.')
            }            
          },
          error: (error) => {
            console.log(error)
            console.log('clearing UserService.')
            this.userService.clearService();
            this.signInForm.reset();
            // print message to user.
            this.returnedError = error.error;
          },
          complete: () => {}
        })
      }
    }  
  }

  clearError() {
    this.returnedError = undefined
  }


}

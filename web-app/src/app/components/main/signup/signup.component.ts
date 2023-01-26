import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent implements OnInit {

  //  todo add own validators 
  signUpForm = new FormGroup({
    login: new FormControl('', [Validators.required, Validators.minLength(6)]),
    password: new FormControl('', [Validators.required, Validators.minLength(6)])  
  });

  public returnedError: any | undefined;

  constructor(private userService: UserService, private router: Router) { }

  ngOnInit(): void {
  }

  onSubmit() {
    const login = this.signUpForm.value.login;
    const pass = this.signUpForm.value.password;
    if (login && pass ) {
      const signup = this.userService.signUp(login, pass)
      if ( signup ){
        signup.subscribe({
          next: (response) => {
            if (response.status === 200) {
              this.userService.assignSubscriptions()
              this.userService.setUserAndSettings(
                response.body?.user,
                response.body?.settings
              );
              this.userService.connectViaWebsocket()
              // after successfull request we should update KSID cookie 
              // to have correct userId
              // this.userService.updateSession()
              // this.userService.setLogoutTimer()
              // and redirect to user site
              this.router.navigate(['user']);
            } else {
              console.log('sign in status other than 200.')
            }
          },
          error: (error) => {
            console.log(error);
            console.log('clearing UserService.')
            this.userService.clearService();
            this.signUpForm.reset();
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

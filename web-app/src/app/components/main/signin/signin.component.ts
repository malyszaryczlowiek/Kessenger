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
    login: new FormControl('', [Validators.required, Validators.minLength(6)]),
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
    
            // we save created user 
            this.userService.setUserAndSettings(
              response.body?.user,
              response.body?.settings
            );
  
            // after successfull request we should update KSID cookie.
            // this.userService.
            
            // and redirect to user site
            this.router.navigate(['user']);
          },
          error: (error) => {
            console.log(error);
            console.log('clearing UserService.')
            this.userService.clearService();
  
            this.signInForm.reset();
            // this.signInForm.value.login. = '';
            ////this.signInForm.value.password = '';
  
            // print message to user.
            this.returnedError = error;
          },
          complete: () => {}
        })
      }
    }  
  }





}

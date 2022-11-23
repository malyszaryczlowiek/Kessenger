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
  
  
  bad = false;

  constructor(private userService: UserService) { }

  ngOnInit(): void {
  }

  onSubmit() {
    const login = this.signInForm.value.login;
    const pass = this.signInForm.value.password;
    if (login && pass ) { 
      this.userService.signIn(login, pass)
      
      /* .subscribe({
        next: () => {} ,
        error: (error) => {
          console.log(error)
        },
        
      }) */
    }
  }

}

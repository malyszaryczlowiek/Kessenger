import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent implements OnInit {

  signUpForm =  new FormGroup({
    login: new FormControl('', [Validators.required, Validators.minLength(6)]),
    password: new FormControl('', [Validators.required, Validators.minLength(6)])  //  todo dodać walidatory
  });

  constructor(private userService: UserService) { }

  ngOnInit(): void {
  }

  onSubmit() {
    const login = this.signUpForm.value.login;
    const pass = this.signUpForm.value.password;
    if (login && pass ) {
      this.userService.signUp(login, pass)
    }    
  }

}

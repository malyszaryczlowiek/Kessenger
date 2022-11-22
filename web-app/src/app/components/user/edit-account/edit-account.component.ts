import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-edit-account',
  templateUrl: './edit-account.component.html',
  styleUrls: ['./edit-account.component.css']
})
export class EditAccountComponent implements OnInit {

  
  // in minutes
  sessionDuration: number = 15;



  loginSuccessfullChanged = false;
  loginTaken = false;
  sessionSuccessfullChanged = false;
  passwordSuccessfullChanged = false;
  

  // controls
  loginFormGroup = new FormGroup({
    loginForm: new FormControl('', [Validators.required, Validators.minLength(8)])
  });

  sessionGroup = new FormGroup({
    sessionControl : new FormControl(15, [Validators.required, Validators.max(15), Validators.min(1)])
  });
  
  // TODO dostawić powturzenie hasła i sprawdzić czy są takie same
  passGroup = new FormGroup({
    old: new FormControl('', [Validators.required, Validators.minLength(8)]),
    neww: new FormControl('', [Validators.required, Validators.minLength(8)])
  });


  


  constructor(private userService: UserService) { }

  ngOnInit(): void {
  }




  saveLogin() {

  }




  changePassword() {

  }



  changeSessionTime() {

  }

}

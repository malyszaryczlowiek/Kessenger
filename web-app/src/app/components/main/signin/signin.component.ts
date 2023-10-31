import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
// services
import { ConnectionService } from 'src/app/services/connection.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';

@Component({
  selector: 'app-signin',
  templateUrl: './signin.component.html',
  styleUrls: ['./signin.component.css']
})
export class SigninComponent implements OnInit {
 
  signInForm = new FormGroup({
    login: new FormControl('', [Validators.required, Validators.minLength(4)]),
    password: new FormControl('', [Validators.required, Validators.minLength(6)])  //  todo add validators
  }); 
  
  // @Output() errorMessageEmitter: EventEmitter<string> = new EventEmitter<string>()


  constructor(
              private connectionService: ConnectionService,
              private responseNotifier: ResponseNotifierService,
              private router: Router
              // private loadBalancer: LoadBalancerService, 
              // in case if we have already opened session in other server than current one
              // and we need to rebalance to opened one
              ) { }

  ngOnInit(): void {
  }



  onSubmit() {
    const login = this.signInForm.value.login;
    const pass = this.signInForm.value.password;
    if (login && pass) { this.signIn(login, pass) }
  }



  private signIn(login: string, pass: string) {
    const signin = this.connectionService.signIn(login, pass)
    if ( signin ){
      signin.subscribe({
        next: (response) => {
          if (response.status === 200) {
            const body = response.body
            if ( body ) {
              this.connectionService.initialize(
                body.user,
                body.settings,
                body.chatList
              )
              this.router.navigate(['user']);
            }              
          } else {
          }            
        },
        error: (error) => {
          this.responseNotifier.handleError(error)                                  
          console.log(error)
          this.connectionService.disconnect()
          this.signInForm.reset();
        },
        complete: () => {}
      })
    }
  }


}

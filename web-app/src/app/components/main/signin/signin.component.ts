import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LoadBalancerService } from 'src/app/services/load-balancer.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
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
  
  // @Output() errorMessageEmitter: EventEmitter<string> = new EventEmitter<string>()


  constructor(private userService: UserService, 
              private responseNotifier: ResponseNotifierService,
              // private loadBalancer: LoadBalancerService,
              private router: Router) { }

  ngOnInit(): void {
  }



  onSubmit() {
    const login = this.signInForm.value.login;
    const pass = this.signInForm.value.password;
    if (login && pass) { this.signIn(login, pass) }
  }



  private signIn(login: string, pass: string) {
    const signin = this.userService.signIn(login, pass)
    if ( signin ){
      signin.subscribe({
        next: (response) => {
          if (response.status === 200) {

            const body = response.body
            if ( body ) {
              this.userService.assignSubscriptions()
              this.userService.setUserAndSettings(
                body.user,
                body.settings
              );
              // after successfull request we should update KSID cookie 
              // to have correct userId
              this.userService.updateSession(false)
              this.userService.setChats( body.chatList )
              // this.userService.connectViaWebsocket() ttt
              this.router.navigate(['user']);
            }              
          } else {
            
          }            
        },
        error: (error) => {
          // if status is 0 this means backend service is 
          // unavailable so we need rebalance and try call 
          // again
          /* if (error.status == 0) {
            this.loadBalancer.rebalance()
            this.signIn(login, pass)
          } 
          else { */
            this.responseNotifier.handleError(error)                                  
            console.log(error)
            console.log('clearing UserService.')
            this.userService.clearService();
            this.signInForm.reset();
          //}          
        },
        complete: () => {}
      })
    }
  }


}

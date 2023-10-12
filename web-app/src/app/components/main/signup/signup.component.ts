import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ConnectionService } from 'src/app/services/connection.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';


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

  constructor(private connectionService: ConnectionService,
              private responseNotifier: ResponseNotifierService,
              private router: Router) { }

  
  
  ngOnInit(): void { }




  onSubmit() {
    const login = this.signUpForm.value.login;
    const pass = this.signUpForm.value.password;
    if (login && pass ) {
      const signup = this.connectionService.signUp(login, pass)
      if ( signup ){
        signup.subscribe({
          next: (response) => {
            if (response.status === 200) {
              const user = response.body?.user
              const settings = response.body?.settings
              if (user && settings)
              this.connectionService.initialize(
                user,
                settings,
                new Array()
              )
              this.connectionService.connectViaWebsocket()
              

              // stare
              //this.userService.assignSubscriptions()
              // this.userService.setUserAndSettings(
              //   response.body?.user,
              //  response.body?.settings
              // );
              // this.userService.connectViaWebsocket()
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
            this.responseNotifier.handleError(error)
            console.log(error);
            console.log('clearing UserService.')



            // UWAGA to jeszcze trzeba sprawdzić 
            // czy klearwoanie będzie wyłączało wszystko co trzeba wyłączyć 

            this.connectionService.disconnect() 
            // this.connectionService.clearService();
            this.signUpForm.reset();

          },
          complete: () => {}
        })  
      }
      
    }    
  }




  

}

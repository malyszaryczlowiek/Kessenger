import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { Toast } from 'bootstrap'

import { UserService } from 'src/app/services/user.service';
import { HttpErrorHandlerService } from 'src/app/services/http-error-handler.service';

@Component({
  selector: 'app-main',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class MainComponent implements OnInit, OnDestroy {


  error: {header: string, message: string} | undefined
  errorMessageSubscription: Subscription | undefined

  /*
    Jak tylko wchodizmy na stronę to UserService sprawdza, czy
    mamy ksid 
      - jeśli tak to wysyła zapytanie do servera czy sesja jest aktywna 
        - jeśli jest aktywna do odsyła wszystkie dane użytkownika,
          które są zapisywne w Userservice i przekierowuje do /user
        - jeśli nie jest aktywna to nie robi nic i czeka aż user się zaloguje
      - jeśli nie mmay ksid bo wygasł to generujemy nowy ksid i wysyłamy 
        jako ciasteczko wraz z login credentials. 


    Jeśli otrzymamy odpowiedź o błędzie serwera to nalezy wyświetlić komunikat,
    że servis jest niedostępny i pozostać na stronie.      
  */
  

  constructor(private router: Router, 
              private httpErrorHandler: HttpErrorHandlerService,
              private userService: UserService) {
  }



  ngOnInit(): void {
    if ( this.userService.user ) 
      this.router.navigate(['user'])

    this.errorMessageSubscription = this.httpErrorHandler.errorMessageEmitter.subscribe(
      (e) => {
        this.error = e
        const toastId = document.getElementById('error_main_toast')
        if ( toastId ){
          const toast = new Toast(toastId)
          toast.show() 
        }
      }
    )  
  }



  ngOnDestroy(): void {
    if (this.errorMessageSubscription) this.errorMessageSubscription.unsubscribe()
  }



}

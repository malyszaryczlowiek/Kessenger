import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Toast } from 'bootstrap'
import { HtmlService } from 'src/app/services/html.service';
import { UserService } from 'src/app/services/user.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';



@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.css']
})
export class UserComponent implements OnInit, OnDestroy {

  error: {header: string, code: number, message: string} | undefined
  errorMessageSubscription: Subscription | undefined


  constructor(private userService: UserService, 
              private htmlService: HtmlService,
              private responseNotifier: ResponseNotifierService) { }


  
  ngOnInit(): void {
    console.log('UserComponent.ngOnInit()')
    if ( ! this.userService.isWSconnectionDefined() ) this.userService.connectViaWebsocket() 
    this.errorMessageSubscription = this.responseNotifier.responseEmitter.subscribe(
      (e) => {
        this.error = e
        const toastId = document.getElementById('error_user_toast')
        if ( toastId ) {
          const toast = new Toast(toastId)
          toast.show() 
        }
      }
    )  
  }


  ngOnDestroy(): void {
    console.log('UserComponent.ngOnDestroy()')
    if ( this.errorMessageSubscription ) this.errorMessageSubscription.unsubscribe()
  }


}

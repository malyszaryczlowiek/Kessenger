import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Toast } from 'bootstrap';
// services
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
// import { HtmlService } from 'src/app/services/html.service';
// import { ConnectionService } from 'src/app/services/connection.service';



@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.css']
})
export class UserComponent implements OnInit, OnDestroy {

  error: {header: string, code: number, message: string} | undefined
  errorMessageSubscription: Subscription | undefined


  constructor(//private connectionService: ConnectionService,
              private responseNotifier: ResponseNotifierService) { }


  
  ngOnInit(): void {
    console.log('UserComponent.ngOnInit()')
    // if ( ! this.userService.isWSconnectionDefined() ) this.userService.connectViaWebsocket() 
    // possibly to delete
    // if ( ! this.connectionService.isWSconnectionDefined() ) this.connectionService.connectViaWebsocket() 
    
    // for printing notifications -> probably to delete because of existence the same subscription in main.component
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

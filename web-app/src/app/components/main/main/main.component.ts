import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { Toast } from 'bootstrap'
// services
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
import { ConnectionService } from 'src/app/services/connection.service';

@Component({
  selector: 'app-main',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class MainComponent implements OnInit, OnDestroy {


  error: {header: string, code: number, message: string} | undefined
  errorMessageSubscription: Subscription | undefined
  
  

  constructor(private router: Router, 
              private responseNotifier: ResponseNotifierService,
              private connectionService: ConnectionService ) {
  }



  ngOnInit(): void {
    console.log('MainComponent.ngOnInit()')
    // this subscription shows all error and information notification as toast object
    this.errorMessageSubscription = this.responseNotifier.responseEmitter.subscribe(
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
    console.log('MainComponent.ngOnDestroy()')
    if (this.errorMessageSubscription) this.errorMessageSubscription.unsubscribe()
  }



}

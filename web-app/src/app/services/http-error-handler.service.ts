import { EventEmitter, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class HttpErrorHandlerService {

  errorMessageEmitter: EventEmitter<{header: string, message: string}> = new EventEmitter<{header: string, message: string}>()

  constructor() { }


  handle(error: any) {
    if (error.status == 0){
      const body = {
        header: 'Connction Error',
        message: 'Service is unavailable :('
      }
      this.errorMessageEmitter.emit( body )
    } 
    else { // if (error.code == 401)
      const body = {
        header: 'Error',
        message: `${error.error.message}`
      }
      this.errorMessageEmitter.emit( body )
    }
  }


  printError(body: {header: string, message: string}) {
    this.errorMessageEmitter.emit( body )
  }

}

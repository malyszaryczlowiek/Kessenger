import { EventEmitter, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ResponseNotifierService {
  
  // {header: string, code: number, message: string}
  responseEmitter: EventEmitter<any> = new EventEmitter<any>()
  logoutEmitter:   EventEmitter<any> = new EventEmitter<any>()

  constructor() { }


  handleError(error: any) {
    if (error.status == 0){
      const body = {
        header: 'Connction Error',
        code: 1,
        message: 'Service is unavailable :('
      }
      this.responseEmitter.emit( body )
    } 
    if (error.status == 401){
      this.logoutEmitter.emit()
    }
    else { 
      const body = {
        header: 'Error',
        code: error.error.num,
        message: `${error.error.message}`
      }
      this.responseEmitter.emit( body )
    }
  }

 
  printError(body: {header: string, code: number, message: string}) {
    this.responseEmitter.emit( body )
  }


  printNotification(b: {header: string, message: string}) {
    const body = {
      header: b.header,
      code: 0,
      message: b.message
    }
    this.responseEmitter.emit( body )
  }


}

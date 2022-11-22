import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class UtctimeService {

  /* private epochTime 
  g = 
  = new Date(1970,1,1,0,0,0,0) */

  constructor() { 
    console.log('utc time: ' + `${this.getUTCmilliSeconds()}`)
    // console.log('utc time: ' + `${this.g}`)
  }

  getUTCmilliSeconds(): number {
    const noww = new Date()
    return Date.UTC(
      noww.getUTCFullYear(),
      noww.getUTCMonth(),
      noww.getUTCDate(),
      noww.getUTCHours(),
      noww.getUTCMinutes(),
      noww.getUTCSeconds(),
      noww.getUTCMilliseconds()) ;
  }


}

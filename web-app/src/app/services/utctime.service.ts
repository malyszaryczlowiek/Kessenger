import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class UtctimeService {

  constructor() { 
    console.log('utc time: ' + `${this.getUTCmilliSeconds()}`)
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


  getDate(millis: number, zoneId: string): string {
    const date = new Intl.DateTimeFormat("en", {
      timeZone: zoneId,
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      weekday: "long",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false, 
      second: "2-digit",
      timeZoneName: "short",
      hourCycle: "h23"
    }).format( millis )
    return date
  }


}

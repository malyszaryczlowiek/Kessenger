import { Injectable } from '@angular/core';

import { UserSettingsService } from './user-settings.service';
import { CookieService } from 'ngx-cookie-service';
import { UtctimeService } from './utctime.service';
import { Ksid } from '../models/Ksid';
import { v4 as uuidv4 } from 'uuid';
import { Router } from '@angular/router';


@Injectable({
  providedIn: 'root'
})
export class SessionService {

  private ksid?: Ksid;



  constructor(private cookieService: CookieService,
              private utcService: UtctimeService,
              private userSettings: UserSettingsService,
              private router: Router) {
    this.fetchKsid();
  }

  private fetchKsid() {
    const k = this.cookieService.get('KSID')
    if (k != '') {
      const arr = k.split('__');
      this.ksid = new Ksid(arr[0], arr[1], arr[2] as unknown as number); 
    } else {
      this.ksid = undefined;
    }
  }



  isSessionValid(): boolean {
    this.fetchKsid();
    if (this.ksid) return true;
    else return false;
  }
   

  setNewSession(userId: string) {
    // time is validity time of session. after this time session will be invalid 
    //   unless user will actualize  session making some request. 
    const time: number = this.utcService.getUTCmilliSeconds() + this.userSettings.settings.sessionDuration; // current  time + 15 min
    this.ksid = new Ksid(uuidv4(), userId , time);
    this.cookieService.set('KSID', this.ksid.toString(), this.getExpirationTime(), '/');
  }


  updateSession(userId: string) {
    this.fetchKsid();
    if ( this.ksid ) {
      const time: number = this.utcService.getUTCmilliSeconds() + this.userSettings.settings.sessionDuration; // current  time + 15 min
      this.ksid = new Ksid(this.ksid.sessId, userId, time);
      this.cookieService.set('KSID', this.ksid.toString(), this.getExpirationTime(), '/');
    } else {
      this.router.navigate(['session-timeout'])
    }   
  }


  invalidateSession() {
    this.cookieService.delete('KSID');
    this.ksid = undefined;
  }



/*   isDefined(): boolean {
    if (this.ksid) return true;
    else return false;
  }
 */

  getSessionToken(): string | undefined  {
    this.fetchKsid();
    if (this.ksid) {
      return  this.ksid?.toString();
    } else  return undefined 
  }


  






  // private // expiration time (in days) used in cookies
  private getExpirationTime(): number {
    const part = this.userSettings.settings.sessionDuration / 3600000
    return part / 24;
  }

  
  getSavedUserId(): string | undefined {
    this.fetchKsid()
    if (this.ksid) return this.ksid.userId;
    else return undefined;
  }


  
  



/*   saveKsid(userId: string) {
    const time: number = this.utcService.getUTCmilliSeconds();
    this.ksid = new Ksid(uuidv4(), userId, time);    //  `${uuidv4()}__${userId}__${time}`;
    this.cookieService.set('ksid', this.ksid.toString(), 0.01041667);
  }
 */

}

import { EventEmitter, Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';
import { v4 as uuidv4 } from 'uuid';
import { Router } from '@angular/router';
// services
import { UserSettingsService } from './user-settings.service';
import { UtctimeService } from './utctime.service';
// models
import { Ksid } from '../models/Ksid';
import { User } from '../models/User';
import { Subscription } from 'rxjs';



@Injectable({
  providedIn: 'root'
})
export class SessionService {

  private ksid?: Ksid;

  logoutTimer: NodeJS.Timeout | undefined;
  // logoutSeconds:       number | undefined;
  logoutSeconds:       number = 5; // to może powodować błędy i będzie jakoś trzeba 

  logoutSecondsEmitter: EventEmitter<number> = new EventEmitter()
  logoutEmitter:        EventEmitter<number> = new EventEmitter()


  settingsChangeSubscription: Subscription | undefined






  constructor(private cookieService: CookieService,
              private utcService: UtctimeService,
              private userSettings: UserSettingsService,
              private router: Router) {
    this.fetchKsid();
  }



  initialize(user: User) {
    // jak tylko zostanie zainicjalizowany to trzeba 

    // tutaj trzeba subskrybenta, który będzie nasłuchiwał zmian w settings-service
    if ( this.settingsChangeSubscription ) {
      this.settingsChangeSubscription = this.userSettings.settingsChangeEmitter.subscribe(
        (settings) => {
          // zmiana ilości sekund do wylogowania
          this.logoutSeconds = settings.sessionDuration / 1000
          // restart logoutTimera
          this.restartLogoutTimer()
        }
      )
    }

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
    const time: number = this.utcService.getUTCmilliSeconds() + this.userSettings.settings.sessionDuration; 
    this.ksid = new Ksid(uuidv4(), userId , time);
    this.cookieService.set('KSID', this.ksid.toString(), this.getExpirationTime(), '/');
  }


  updateSession(userId: string) {
    this.fetchKsid();
    if ( this.ksid ) {
      const time: number = this.utcService.getUTCmilliSeconds() + this.userSettings.settings.sessionDuration; 
      this.ksid = new Ksid(this.ksid.sessId, userId, time);
      // this.cookieService.delete('KSID')
      this.cookieService.set('KSID', this.ksid.toString(), this.getExpirationTime(), '/');
      this.restartLogoutTimer()  // dodane 
    } else {
      this.router.navigate(['session-timeout'])
    }   
  }

  getKsid(): Ksid | undefined {
    return this.ksid
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


  
  


  // newly added


  restartLogoutTimer() {
    // this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
    if ( this.logoutSeconds ) {
      if (this.logoutTimer ) clearInterval( this.logoutTimer )    //   To DODAŁEM
      this.logoutTimer = setInterval(() => {
        this.logoutSeconds = this.logoutSeconds - 1
        this.logoutSecondsEmitter.emit(this.logoutSeconds)
        if (this.logoutSeconds < 1) {
          console.log('LogoutTimer called!!!')
          clearInterval(this.logoutTimer)
          this.clearService()
          this.router.navigate([''])
        }
      }, 1000)
    }
/*     if (this.logoutTimer) {}
    else {
        
    }
 */

  }



  setLogoutTime(timeInSeconds: number) {
    this.logoutSeconds = timeInSeconds
  }


  clearService() {
    if (this.logoutTimer) clearInterval(this.logoutTimer)  
    this.logoutTimer = undefined

    
  }



/*   saveKsid(userId: string) {
    const time: number = this.utcService.getUTCmilliSeconds();
    this.ksid = new Ksid(uuidv4(), userId, time);    //  `${uuidv4()}__${userId}__${time}`;
    this.cookieService.set('ksid', this.ksid.toString(), 0.01041667);
  }
 */

}

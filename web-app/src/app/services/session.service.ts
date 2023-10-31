import { EventEmitter, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { CookieService } from 'ngx-cookie-service';
import { v4 as uuidv4 } from 'uuid';
import { Subscription } from 'rxjs';
// services
import { UserSettingsService } from './user-settings.service';
import { UtctimeService } from './utctime.service';
// models
import { Ksid } from '../models/Ksid';
import { User } from '../models/User';




@Injectable({
  providedIn: 'root'
})
export class SessionService {

  private ksid?: Ksid;

  logoutTimer: NodeJS.Timeout | undefined;
  logoutSeconds: number = 0; 

  logoutSecondsEmitter: EventEmitter<number> = new EventEmitter()
  logoutEmitter:        EventEmitter<number> = new EventEmitter()


  settingsChangeSubscription: Subscription | undefined


  
  constructor(private cookieService: CookieService,
              private utcService: UtctimeService,
              private userSettings: UserSettingsService,
              private router: Router) {
                console.log('SessionService.constructor()')
    this.fetchKsid();
  }



  initialize(user: User) {
    console.log('SessionService.initialize()')
    // tutaj trzeba subskrybenta, który będzie nasłuchiwał zmian w settings-service
    if ( ! this.settingsChangeSubscription ) {
      this.settingsChangeSubscription = this.userSettings.settingsChangeEmitter.subscribe(
        (settings) => {
          this.logoutSeconds = settings.sessionDuration / 1000
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
    console.log('SessionService.isSessionValid() -> ', this.fetchKsid())
    if (this.ksid) return true;
    else return false;
  }
   


  setNewSession(userId: string) {
    console.log('SessionService.setNewSession()')
    // time is validity time of session. after this time session will be invalid 
    // unless user will actualize  session making some request. 
    const time: number = this.utcService.getUTCmilliSeconds() + this.userSettings.settings.sessionDuration; 
    this.ksid = new Ksid(uuidv4(), userId , time);
    this.cookieService.set('KSID', this.ksid.toString(), this.getExpirationTime(), '/');
  }



  updateSession(userId: string) {
    console.log('SessionService.updateSession()')
    this.fetchKsid();
    if ( this.ksid ) {
      console.log('SessionService.updateSession() -> KSID is valid')
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
    console.log('SessionService.invalidateSession()')
    this.cookieService.delete('KSID');
    this.ksid = undefined;
  }



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



  restartLogoutTimer() {
    console.log('SessionService.restartLogoutTimer()')
    this.logoutSeconds = this.userSettings.settings.sessionDuration / 1000
    if ( this.logoutSeconds ) {
      if (this.logoutTimer ) clearInterval( this.logoutTimer )   
      this.logoutTimer = setInterval(() => {
        this.logoutSeconds = this.logoutSeconds - 1
        this.logoutSecondsEmitter.emit(this.logoutSeconds)
        if (this.logoutSeconds < 1) {
          console.log('SessionService.logoutTimer -> clearing itself')
          clearInterval(this.logoutTimer)
          console.log('SessionService.logoutTimer -> logoutEmitter -> emiting event to call ConnectionService.disconnect()')
          this.logoutEmitter.emit( 0 )
        }
      }, 1000)
    }
  }



  setLogoutTime(timeInSeconds: number) {
    console.log('SessionService.setLogoutTime()')
    this.logoutSeconds = timeInSeconds
  }



  clearService() {
    console.log('SessionService.clearService()')
    if (this.logoutTimer) clearInterval(this.logoutTimer)  
    if ( this.settingsChangeSubscription ) { this.settingsChangeSubscription.unsubscribe(); }
    this.logoutTimer = undefined
  }

}

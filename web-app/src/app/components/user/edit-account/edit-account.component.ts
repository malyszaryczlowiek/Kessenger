import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Settings } from 'src/app/models/Settings';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-edit-account',
  templateUrl: './edit-account.component.html',
  styleUrls: ['./edit-account.component.css']
})
export class EditAccountComponent implements OnInit, OnDestroy {

  settings: Settings | undefined
  defaultZone = 'UTC'
  zones: string[] = []
  seconds: number = 0


  settingsResponse: any | undefined
  loginResponse: any | undefined
  passwordResponse: any | undefined  
  returnedError: any | undefined



  settingsGroup = new FormGroup({
    sessionControl : new FormControl(this.settingsService.settings.sessionDuration / (60000), [Validators.required, Validators.max(15), Validators.min(1)]),
    zoneControl : new FormControl(this.settingsService.settings.zoneId, [Validators.required, Validators.max(15), Validators.min(1)])
  })

  loginFormGroup = new FormGroup({
    loginForm: new FormControl('', [Validators.required, Validators.minLength(4)])
  });

  // TODO dostawić powtórzenie hasła i sprawdzić czy są takie same
  passGroup = new FormGroup({
    old: new FormControl('', [Validators.required, Validators.minLength(8)]),
    neww: new FormControl('', [Validators.required, Validators.minLength(8)])
  });

  logoutSecondsEmitter: EventEmitter<number> = this.userService.logoutSecondsEmitter
  


  constructor(private userService: UserService, private settingsService: UserSettingsService, private router: Router) { }

  ngOnInit(): void {
    this.zones = this.settingsService.zones
    this.settings = this.settingsService.settings
    this.settingsGroup.controls.zoneControl.setValue( this.settings.zoneId )
    this.settingsGroup.controls.sessionControl.setValue( this.settings.sessionDuration / (60000) )
    this.seconds = this.settingsService.settings.sessionDuration / 1000
    this.userService.updateSession()
    this.logoutSecondsEmitter.subscribe(
      (seconds: number) => this.seconds = seconds
    )
  }


  ngOnDestroy(): void {
    // this.logoutSecondsEmitter.unsubscribe()
  }




  saveSettings() {
    const time = this.settingsGroup.controls.sessionControl.value
    const zone = this.settingsGroup.controls.zoneControl.value
    if (this.settings && time && zone) {
      const body: Settings = {
        joiningOffset: this.settingsService.settings.joiningOffset,
        sessionDuration: time * 60 * 1000,
        zoneId: zone
      }
      const oldSett = this.settingsService.settings
      this.settingsService.setSettings( body )
      const obs = this.userService.changeSettings( body )
      if ( obs ) {
        obs.subscribe({
          next: (response) => {
            this.settingsResponse = response.body
            // this.userService.restartLogoutTimer() 
            this.userService.updateSession()
          },
          error: (error) => {
            this.settingsService.setSettings( oldSett )
            console.log("ERROR", error)
            if (error.status == 401){
              console.log('Session is out.')
              this.userService.clearService()
              this.router.navigate(['session-timeout'])
            }
            else {
              this.returnedError = error.error
            }
          },
          complete: () => {
            console.log("completed")
          }
        })
      } else {
        this.router.navigate(['session-timeout'])
      }
    }
  }




  saveLogin() {
    const newLogin = this.loginFormGroup.controls.loginForm.value
    if ( newLogin ) {
      const l = this.userService.changeLogin(newLogin)
      if ( l ) {
        l.subscribe({
          next: (response) => {
            const b = response.body
            if ( b ) { 
              this.userService.updateLogin(newLogin)
              console.log(`new login ${b}`)
              this.loginResponse = b
            }
          },
          error: (error) => {
            console.log("ERROR", error)
            if (error.status == 401){
              console.log('Session is out.')
              this.userService.clearService()
              this.router.navigate(['session-timeout'])
            }
            if (error.status == 400) {
              this.loginResponse = error.error
              /* if (error.error.message == 'Login taken. Try with another one.') {
                this.loginTaken = true
              }
               */
            }
            else {
              this.returnedError = error.error
            }
          },
          complete: () => {},
        })
      } else 
        this.router.navigate(['session-timeout'])
    }
  }




  changePassword() {
    const oldPass = this.passGroup.controls.old.value
    const newPass = this.passGroup.controls.neww.value
    if (oldPass && newPass) {
      const p = this.userService.changePassword(oldPass, newPass)
      if ( p ) {
        //tutuaj kontynuowaća
        p.subscribe({
          next: (response) => {
            if (response.status == 200) {
              this.passwordResponse = response.body
            }
          },
          error: (err) => {
            if (err.status == 400) {
              this.passwordResponse = err.error
            }
            if (err.status == 401){
              console.log('Session is out.')
              this.userService.clearService()
              this.router.navigate(['session-timeout'])
            }
          },
          complete: () => {}
        })
      } 
      else 
        this.router.navigate(['session-timeout'])
    }
  }

  clearSettingsNotification() {
    this.userService.updateSession()
    this.settingsResponse = undefined
  }

  clearLoginNotification() {
    this.userService.updateSession()
    this.loginResponse = undefined
  }

  clearPasswordNotification() {
    this.userService.updateSession()
    this.passwordResponse = undefined
  }

  clearError() {
    this.userService.updateSession()
    this.returnedError = undefined
  }

  



}

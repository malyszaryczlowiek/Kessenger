import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { Settings } from 'src/app/models/Settings';
import { ConnectionService } from 'src/app/services/connection.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-edit-account',
  templateUrl: './edit-account.component.html',
  styleUrls: ['./edit-account.component.css']
})
export class EditAccountComponent implements OnInit, OnDestroy {

  settings: Settings | undefined
  zones: string[] = []
  /* settingsResponse:  any | undefined
  loginResponse:     any | undefined
  passwordResponse:  any | undefined  
   */
  fechingSubscription: Subscription | undefined


  settingsGroup = new FormGroup({
    sessionControl : new FormControl(this.settingsService.settings.sessionDuration / (60000), [Validators.required, Validators.max(15), Validators.min(1)]),
    zoneControl : new FormControl(this.settingsService.settings.zoneId, [Validators.required, Validators.max(15), Validators.min(1)])
  })

  loginFormGroup = new FormGroup({
    loginForm: new FormControl('', [Validators.required, Validators.minLength(4)])
  });

  // TODO write custom validators
  passGroup = new FormGroup({
    old: new FormControl('', [Validators.required, Validators.minLength(8)]),
    neww: new FormControl('', [Validators.required, Validators.minLength(8)])
  });


  


  constructor(private connectionService: ConnectionService, 
              private settingsService: UserSettingsService, 
              private responseNotifier: ResponseNotifierService,
              private router: Router) { }

  ngOnInit(): void {
    this.fechingSubscription = this.connectionService.dataFetchedEmitter.subscribe(
      ( b ) => {
        this.zones = this.settingsService.zones
        this.settings = this.settingsService.settings
        this.settingsGroup.controls.zoneControl.setValue( this.settings.zoneId )
        this.settingsGroup.controls.sessionControl.setValue( this.settings.sessionDuration / (60000) )
      }
    )
    if ( this.connectionService.isWSconnected() ) this.connectionService.dataFetchedEmitter.emit( 0 )
  }




  ngOnDestroy(): void {
    if (this.fechingSubscription) this.fechingSubscription.unsubscribe()
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
      const obs = this.connectionService.changeSettings( body )
      if ( obs ) {
        obs.subscribe({
          next: (response) => {

            this.settingsService.setSettings( body )
            this.connectionService.updateSession()
            const print = {
              header: 'Update',
              //code: 0,
              message: 'Settings saved.'
            }
            this.responseNotifier.printNotification( print )
          },
          error: (error) => {
            this.responseNotifier.handleError( error ) 
          },
          complete: () => {}
        })
      } else {
        this.router.navigate(['session-timeout'])
      }
    }
  }




  saveLogin() {
    const newLogin = this.loginFormGroup.controls.loginForm.value
    if ( newLogin ) {
      const l = this.connectionService.changeLogin(newLogin)
      if ( l ) {
        l.subscribe({
          next: (response) => {
            const b = response.body
            if ( b ) { 
              this.connectionService.updateUserLogin( newLogin )
              const print = {
                header: 'Update',
                //code: 0,
                message: b.message
              }
              this.responseNotifier.printNotification( print )
              
            }
          },
          error: (error) => {
            console.log("ERROR", error)
            this.responseNotifier.handleError( error )
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
      const p = this.connectionService.changePassword(oldPass, newPass)
      if ( p ) {
        p.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const print = {
                header: 'Update',
                //code: 0,
                message: 'Password changed.'
              }
              this.responseNotifier.printNotification( print )
              this.connectionService.updateSession()
            }
          },
          error: (err) => {
            if (err.status == 400) {
              const print = {
                header: 'Error',
                code: err.error.num,
                message: err.error.message
              }
              this.responseNotifier.printNotification( print )
            }
            if (err.status == 401){
              console.log('Session is out.')
              this.connectionService.disconnect()
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


}

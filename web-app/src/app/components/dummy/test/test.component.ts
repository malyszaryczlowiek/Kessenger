import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Settings } from 'src/app/models/Settings';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-test',
  templateUrl: './test.component.html',
  styleUrls: ['./test.component.css']
})
export class TestComponent implements OnInit {

  settings: Settings | undefined
  defaultZone = 'UTC'
  zones: string[] = []


  settingsChanged = false

  loginSuccessfullChanged = false;
  loginTaken = false;
  passwordSuccessfullChanged = false;
  

  // Controls 
  settingsGroup = new FormGroup({
    sessionControl : new FormControl(15, [Validators.required, Validators.max(15), Validators.min(1)]),
    zoneControl : new FormControl(this.defaultZone, [Validators.required, Validators.max(15), Validators.min(1)])
  })


  loginFormGroup = new FormGroup({
    loginForm: new FormControl('', [Validators.required, Validators.minLength(8)])
  });

  // TODO dostawić powtórzenie hasła i sprawdzić czy są takie same
  passGroup = new FormGroup({
    old: new FormControl('', [Validators.required, Validators.minLength(8)]),
    neww: new FormControl('', [Validators.required, Validators.minLength(8)])
  });


  


  constructor(private userService: UserService, private userSettings: UserSettingsService, private router: Router) { }

  ngOnInit(): void {
    this.zones = this.userSettings.zones
    this.settings = this.userSettings.settings
  }




  saveSettings() {
    const time = this.settingsGroup.controls.sessionControl.value
    const zone = this.settingsGroup.controls.zoneControl.value
    if (this.settings && time && zone) {
      const body: Settings = {
        joiningOffset: this.settings?.joiningOffset,
        sessionDuration: time,
        zoneId:  zone
      }
      const obs = this.userService.changeSettings( body )
      if ( obs ) {
        obs.subscribe({
          next: (response) => {},
          error: (error) => {
            console.log("ERROR", error)
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

  }




  changePassword() {

  }



}

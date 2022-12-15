import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscribable, Subscription } from 'rxjs';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit, OnDestroy {


  logoutSeconds: number = this.settingsService.settings.sessionDuration / 1000
  logoutSecondsSubscription: Subscription | undefined

  constructor(private userService: UserService, private settingsService: UserSettingsService) {}

  ngOnInit(): void {
      this.logoutSecondsSubscription = this.userService.logoutSecondsEmitter.subscribe(num => this.logoutSeconds = num)
  }

  ngOnDestroy(): void {
    if ( this.logoutSecondsSubscription ) { 
      console.log('HeaderComponent.ngOnDestroy() called, and subscription cancelled.')
      this.logoutSecondsSubscription.unsubscribe()
    }
  }


  logout() {
    this.userService.logout();
  }
  

}

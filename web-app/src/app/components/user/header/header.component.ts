import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ConnectionService } from 'src/app/services/connection.service';
import { SessionService } from 'src/app/services/session.service';
import { UserSettingsService } from 'src/app/services/user-settings.service';


@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit, OnDestroy {


  logoutSeconds: number = this.settingsService.settings.sessionDuration / 1000
  logoutSecondsSubscription: Subscription | undefined

  constructor(private connectionService: ConnectionService, private session: SessionService, private settingsService: UserSettingsService) {}

  ngOnInit(): void {
      this.logoutSecondsSubscription = this.session.logoutSecondsEmitter.subscribe(num => this.logoutSeconds = num)
  }

  ngOnDestroy(): void {
    if ( this.logoutSecondsSubscription ) { 
      console.log('HeaderComponent.ngOnDestroy() called, and subscription cancelled.')
      this.logoutSecondsSubscription.unsubscribe()
    }
  }


  logout() {
    tutaj // trzeba wywołać coś na connectionService z logoutem
    // this.userService.logout();
  }

  updateSession() {
    this.connectionService.updateSession()
    // this.user.updateSession( true )
  }
  

}

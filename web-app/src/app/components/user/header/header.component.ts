import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
// services
import { ConnectionService } from 'src/app/services/connection.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
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

  constructor(private connectionService: ConnectionService, 
    private responseNotifier: ResponseNotifierService,
    private session: SessionService, 
    private settingsService: UserSettingsService, 
    private router: Router) {}

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
    this.connectionService.logout()?.subscribe({
      next: (response) => {
        if (response.status === 200) {
          this.responseNotifier.printNotification(
            {
              header: 'Information',
              // code: 0,
              message: 'Logout successfull'
            }
          )
        }
        console.log(`logout received status ${response.status}`)
        console.log('redirection to logging page')
        this.connectionService.disconnect()
        this.router.navigate([''])
        /* const body = response.body
        if ( body ){
          console.log('ConnectionSerivice.constructor() fetching user login and settings' )
          this.initialize( body.user, body.settings, body.chatList )
          //this.setUserAndSettings(body.user, body.settings)
          // this.logoutSeconds = this.settingsService.settings.sessionDuration / 1000
          //this.restartLogoutTimer()
          // this.chatsService.initialize( body.chatList, body.user )
          this.connectViaWebsocket() 
        }
        else {
          // print error message
        } */


      },
      error: (error) => {
        this.responseNotifier.printNotification(
          {
            header: 'Error',
            // code: 0,
            message: 'Logout UNSUCCESSFULL'
          }
        )
        console.error(error) 
        this.connectionService.disconnect()
        //this.clearService() // zmienić na disconnect tak aby pokasował wszystkie dane
        console.log('redirection to logging page')
        this.router.navigate([''])
      },
      complete: () => {}
    })
    // this.userService.logout();
  }

  updateSession() {
    this.connectionService.updateSession()
    // this.user.updateSession( true )
  }
  

}

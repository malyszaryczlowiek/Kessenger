import { Component, OnInit } from '@angular/core';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit {


  logoutSeconds: number = this.settingsService.settings.sessionDuration / 1000
  logoutSecondsEmitter = this.userService.logoutSecondsEmitter

  constructor(private userService: UserService, private settingsService: UserSettingsService) {}

  ngOnInit(): void {
    this.logoutSecondsEmitter.subscribe(num => this.logoutSeconds = num)
  }

  logout() {
    this.userService.logout();
  }
  

}

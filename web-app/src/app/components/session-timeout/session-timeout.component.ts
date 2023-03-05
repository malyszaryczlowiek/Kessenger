import { Component, OnInit } from '@angular/core';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-session-timeout',
  templateUrl: './session-timeout.component.html',
  styleUrls: ['./session-timeout.component.css']
})
export class SessionTimeoutComponent implements OnInit {

  constructor(private userService: UserService) { }

  // we need to clear user Service
  ngOnInit(): void {
    this.userService.clearService();
  }

}

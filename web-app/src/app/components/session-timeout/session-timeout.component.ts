import { Component, OnInit } from '@angular/core';
import { ConnectionService } from 'src/app/services/connection.service';

@Component({
  selector: 'app-session-timeout',
  templateUrl: './session-timeout.component.html',
  styleUrls: ['./session-timeout.component.css']
})
export class SessionTimeoutComponent implements OnInit {

  constructor(private connectionService: ConnectionService) { }

  // we need to clear user Service
  ngOnInit(): void {
    this.connectionService.disconnect()
  }

}

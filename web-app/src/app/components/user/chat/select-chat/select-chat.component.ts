import { Component, OnInit } from '@angular/core';
import { ConnectionService } from 'src/app/services/connection.service';
import { HtmlService } from 'src/app/services/html.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-select-chat',
  templateUrl: './select-chat.component.html',
  styleUrls: ['./select-chat.component.css']
})
export class SelectChatComponent implements OnInit {

  constructor(private connectionService: ConnectionService, private htmlService: HtmlService) { } // private userService: UserService

  ngOnInit(): void {
    this.connectionService.updateSession()
    // this.userService.updateSession(false)
    this.htmlService.resizeSelectChatImmediately()
  }

}

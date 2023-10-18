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

  constructor( private htmlService: HtmlService) { } // private userService: UserService, private connectionService: ConnectionService,

  ngOnInit(): void {
    console.log('SelectChatComponent.ngOnInit()')
    // this.connectionService.updateSession()
    // this.userService.updateSession(false)
    this.htmlService.resizeSelectChatImmediately()
  }

}

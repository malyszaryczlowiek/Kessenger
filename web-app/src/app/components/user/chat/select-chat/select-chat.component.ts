import { Component, OnInit } from '@angular/core';
import { HtmlService } from 'src/app/services/html.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-select-chat',
  templateUrl: './select-chat.component.html',
  styleUrls: ['./select-chat.component.css']
})
export class SelectChatComponent implements OnInit {

  constructor(private userService: UserService, private htmlService: HtmlService) { }

  ngOnInit(): void {
    this.userService.updateSession()
    this.htmlService.resizeSelectChatImmediately()
  }

}

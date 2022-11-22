import { Component, OnInit } from '@angular/core';
import { Chat } from 'src/app/models/Chat';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';



@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.css']
})
export class UserComponent implements OnInit {

  public chats: Array<ChatData> = new Array();

  constructor(private userService: UserService) { }

  ngOnInit(): void {
    this.chats = this.userService.chatAndUsers;
  }

}

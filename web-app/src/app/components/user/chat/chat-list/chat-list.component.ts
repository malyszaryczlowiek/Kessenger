import { Component, Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-list',
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit {

  @Input() chats: Array<ChatData> = new Array<ChatData>();

  constructor(private router: Router) { }

  ngOnInit(): void {
  }

  onClick(c: ChatData) {
    const chatId = c.chat.chatId;
    this.router.navigate(['user', 'chat', chatId]) 
  }

}

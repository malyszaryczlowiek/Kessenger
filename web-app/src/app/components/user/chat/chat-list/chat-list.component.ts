import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-list',
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit, OnDestroy {

  @Input() chats: Array<ChatData> = new Array<ChatData>(); 


  constructor(private userService: UserService, private router: Router ) {}
  

  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
  }


  onClick(c: ChatData) {
    console.log('navigating to chat' + c.chat.chatName)
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
    this.userService.updateSession()
    this.userService.selectedChatEmitter.emit(c) 
  }


  ngOnDestroy(): void {
    console.log('ChatListComponent.ngOnDestroy() called.')
  }

}

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

  constructor(private userService: UserService, private router: Router) { }

  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
    this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        console.log('')
        if ( b ) {
          console.log('Chats loaded to ChatList')
          this.chats = this.userService.chatAndUsers
          console.log('List has size ' + this.chats.length)
        }
      }
    )
  }

  onClick(c: ChatData) {
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
  }

}

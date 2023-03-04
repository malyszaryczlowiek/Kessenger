import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { Writing } from 'src/app/models/Writing';
import { UserService } from 'src/app/services/user.service';
import { HtmlService } from 'src/app/services/html.service';
import { ChatsDataService } from 'src/app/services/chats-data.service';

@Component({
  selector: 'app-chat-list',
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit, OnDestroy {

  @Input() chats: Array<ChatData> = new Array<ChatData>(); 
  writingSubscription:  Subscription | undefined
  wrt:                       Writing | undefined
  myUserId:                  string  | undefined


  constructor(private userService: UserService, private chatService: ChatsDataService, private router: Router, private htmlService: HtmlService ) {}
  

  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
    this.myUserId = this.userService.user?.userId
    this.writingSubscription = this.userService.getWritingEmmiter().subscribe(
      (w: Writing | undefined) => { this.wrt = w }
    )
    this.htmlService.resizeChatList()
  }


  onClick(c: ChatData) {
    console.log('navigating to chat' + c.chat.chatName)
    //this.userService.fetchOlderMessages( c.chat.chatId )
    this.chatService.markMessagesAsRead( c.chat.chatId )
    this.chatService.selectChat( c.chat.chatId )
    this.userService.updateSession(true)
    const selectedChat = this.userService.getAllChats().find(  (chatData, index, arr) => {
      return chatData.chat.chatId == c.chat.chatId;
    })
    if ( selectedChat ) {
      if ( selectedChat.messages.length == 0 && this.userService.isWSconnected() ) { // 
        this.userService.fetchOlderMessages( c.chat.chatId )
      }      
    }
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
    this.userService.selectedChatEmitter.emit(c) 
  }


  ngOnDestroy(): void {
    console.log('ChatListComponent.ngOnDestroy() called.')
    if ( this.writingSubscription )  this.writingSubscription.unsubscribe()
  }


}

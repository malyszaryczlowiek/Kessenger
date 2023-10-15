import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Router } from '@angular/router';
// services
import { HtmlService } from 'src/app/services/html.service';
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { ConnectionService } from 'src/app/services/connection.service';
// models
import { ChatData } from 'src/app/models/ChatData';
import { Writing } from 'src/app/models/Writing';


@Component({
  selector: 'app-chat-list',
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit, OnDestroy {

  @Input() chats: Array<ChatData> = new Array<ChatData>(); 
  
  wrt:                               Writing | undefined
  receivingWritingSubscription: Subscription | undefined
  // trzeba napisać subscription, które będzie ponownie wczytywało chaty z już zaktualizowanego chat-service
  upToDateChatListSubscription: Subscription | undefined


  // myUserId:                  string  | undefined // ##################################################################       TO zakomentowałem


  constructor(
    private chatService: ChatsDataService,
    private router: Router,
    private htmlService: HtmlService // , private connectionService: ConnectionService
     ) {}
  




  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
    // this.myUserId = this.userService.user?.userId  // ##################################################################       TO zakomentowałem
    this.assignSubscriptions()
    this.htmlService.resizeChatList()
  }





  assignSubscriptions() {
    if ( ! this.receivingWritingSubscription ){
      this.receivingWritingSubscription = this.chatService.receivingWritingEmitter.subscribe(
        (w: Writing | undefined) => { this.wrt = w }
      )
    }
    if ( ! this.upToDateChatListSubscription )  {
      this.upToDateChatListSubscription =  this.chatService.updateChatListEmmiter.subscribe(
        (c) => {
          this.chats = this.chatService.chatAndUsers
        }
      )
    }    
  }




  onClick(c: ChatData) {
    console.log('navigating to chat' + c.chat.chatName)
    // tutaj w selectchat powinniśmy 
    // 1. jeśli lista wiadomości jest pusta powinniśmy fetchować stare wiadomości
    // 2. oznaczyć wszystkie wiadomości jako przeczytane
    // 3. jeśli lista
    this.chatService.selectChat( c.chat.chatId )
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
    
    // stare    
    // this.chatService.fetchOlderMessages( c.chat.chatId ) // ##################################################################       TO zakomentowałem
    /* this.chatService.markMessagesAsRead( c.chat.chatId )
    this.userService.updateSession(true)
    const selectedChat = this.userService.getAllChats().find(  (chatData, index, arr) => {
      return chatData.chat.chatId == c.chat.chatId;
    })
    if ( selectedChat ) {
      if ( selectedChat.messages.length == 0 && this.connectionService.isWSconnected() ) { // 
        this.chatService.fetchOlderMessages( c.chat.chatId )
      }      
    }
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
    this.userService.selectedChatEmitter.emit(c)  */
  }


  ngOnDestroy(): void {
    console.log('ChatListComponent.ngOnDestroy() called.')
    if ( this.receivingWritingSubscription ) this.receivingWritingSubscription.unsubscribe()
    if ( this.upToDateChatListSubscription ) this.upToDateChatListSubscription.unsubscribe()
  }


}

import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Router } from '@angular/router';
// services
import { HtmlService } from 'src/app/services/html.service';
import { ChatsDataService } from 'src/app/services/chats-data.service';
// models
import { ChatData } from 'src/app/models/ChatData';
import { Writing } from 'src/app/models/Writing';
import { ConnectionService } from 'src/app/services/connection.service';


@Component({
  selector: 'app-chat-list',
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit, OnDestroy {

  chats: Array<ChatData> = new Array<ChatData>();  // @Input() -- was mark previously to injecting chat list from chat component
  
  wrt:                               Writing | undefined
  receivingWritingSubscription: Subscription | undefined
  initalizationSubscription:    Subscription | undefined
  upToDateChatListSubscription: Subscription | undefined
  
  constructor( private connectionService: ConnectionService,
               private chatService: ChatsDataService,
               private router: Router,
               private htmlService: HtmlService ) {}
  




  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
    this.assignSubscriptions()
    this.chatService.updateChatList()
    this.chats = this.chatService.chatAndUsers
    this.htmlService.resizeChatList()
  }





  assignSubscriptions() {
    if ( ! this.receivingWritingSubscription ){
      this.receivingWritingSubscription = this.chatService.receivingWritingEmitter.subscribe(
        (w: Writing | undefined) => { this.wrt = w }
      )
    }



    this.initalizationSubscription = this.connectionService.serviceInitializedEmitter.subscribe(
      (n) => {
        console.log('ChatListComponent.initalizationSubscription -> fetched chat list from ChatDataService via serviceInitializedEmitter ')
        this.chats = this.chatService.chatAndUsers
      }
    )

    if ( ! this.upToDateChatListSubscription )  {
      this.upToDateChatListSubscription =  this.chatService.updateChatListEmmiter.subscribe(
        (n) => {
          console.log('ChatListComponent.upToDateChatListSubscription -> fetched chat list from ChatDataService via updateChatListEmmiter ')
          this.chats = this.chatService.chatAndUsers
        }
      )
    }    
  }




  onClick(c: ChatData) {
    console.log('ChatListComponent.onClick() -> navigating to chat', c.chat.chatName)
    this.chatService.selectChat( c.chat.chatId ) // required if we load page from webbrowser,
    this.chatService.updateChatPanel() // called in case if we currently are in one chat
    this.htmlService.scrollDown( true )
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
  }


  ngOnDestroy(): void {
    console.log('ChatListComponent.ngOnDestroy() called.')
    if ( this.receivingWritingSubscription ) this.receivingWritingSubscription.unsubscribe()
    if ( this.initalizationSubscription)     this.initalizationSubscription.unsubscribe()
    if ( this.upToDateChatListSubscription)  this.upToDateChatListSubscription.unsubscribe()
  }


}




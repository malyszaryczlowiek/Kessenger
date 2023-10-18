import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
// services
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { HtmlService } from 'src/app/services/html.service';
// models
import { ChatData } from 'src/app/models/ChatData';
import { Message } from 'src/app/models/Message';
import { Writing } from 'src/app/models/Writing';
import { ConnectionService } from 'src/app/services/connection.service';



@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit, OnDestroy {

  wrt:                              Writing      | undefined
  chatData:                         ChatData     | undefined

  // here we subscribe if we get notification if someone is writing in any chat
  receivingWritingSubscription:     Subscription | undefined
  
  
  // subscribe if we go to top or down of message list
  messageListScrollingSubscription: Subscription | undefined


  // trzeba napisać subscription, które będzie ponownie wczytywało chaty z już zaktualizowanego chat-service
  chatPanelSubscription:            Subscription | undefined



  constructor(private htmlService: HtmlService, 
              private connectionService: ConnectionService,
              private chatService: ChatsDataService,
              private router:      Router,
              private activated:   ActivatedRoute) { }

  

  ngOnInit(): void {
    console.log('ChatPanelComponent.ngOnInit')

    this.chatPanelSubscription = this.chatService.updateChatPanelEmmiter.subscribe(
      (a) => {
        this.chatData = this.chatService.getCurrentChatData()
        
        // if our services are initialized with data but chat was not found, we need to redirect to page-not-found
        /* if ( this.connectionService.isInitlized() && ! this.chatData ) {
          console.log('ChatPanelComponent.chatPanelSubscription  chatId from path not found in list of chats.')
          this.router.navigate(['page-not-found']);
        } */
      }
    )

    
    // we need to stay it because cannot insert Writing value via html. 
    this.receivingWritingSubscription = this.chatService.receivingWritingEmitter.subscribe(
      (w: Writing | undefined) => { 
        if (w && w.chatId == this.chatData?.chat.chatId) {
          this.wrt = w 
        } else {
          this.wrt = undefined
        }        
      }
    )


    this.messageListScrollingSubscription = this.htmlService.messageListScrollEventEmitter.subscribe(
      (position) => {
        if (position == 'down') {
          if (this.chatData) {
            // nowe 
            this.chatService.markMessagesAsRead( this.chatData.chat.chatId ) 
            // ta metoda powinna sama wysyłać event o aktualizację offsetu do backendu przez WS (zrobić to emiterem), ale tylko jeśli liczba nieprzeczytanych wiadomości jest >0, 
            // natomiast samo zjechanie na dół powinno updejtować sesję niezależnie od liczby nieprzeczytanych wiadomości.
            this.htmlService.scrollDown( false )
          }
        }
        if (position == 'top') {
          if (this.chatData) {
            console.log('fetching older messages')
            this.chatService.fetchOlderMessages( this.chatData.chat.chatId )
          }
        }
      } 
    )    


    const chatId = this.activated.snapshot.paramMap.get('chatId');
    if ( chatId ) { 
      this.chatService.selectChat( chatId ) // required if we load page from webbrowser,
      this.chatService.fetchOlderMessages( chatId ) // dodałem na samym końcu
      // not chat list in web application
    }
        
  }




  ngOnDestroy(): void {
    if ( this.chatPanelSubscription )            this.chatPanelSubscription.unsubscribe()
    if ( this.receivingWritingSubscription )     this.receivingWritingSubscription.unsubscribe()
    if ( this.messageListScrollingSubscription ) this.messageListScrollingSubscription.unsubscribe()
    this.chatService.clearSelectedChat()
  }




  sendMessage(m: Message) {
    console.log(`sending message: ${m}`, m)
    this.connectionService.sendMessage( m )
  }




  goToChatSettings() {
    this.connectionService.updateSession()
    this.router.navigate(['user', 'editChat', `${this.chatData?.chat.chatId}`])
  }



}

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


  // subscribe initalization finished
  initalizationSubscription:        Subscription | undefined

  firstLoad = true



  constructor(private htmlService: HtmlService, 
              private connectionService: ConnectionService,
              private chatService: ChatsDataService,
              private router:      Router,
              private activated:   ActivatedRoute) { 
                // console.log(`ChatPanelComponent.constructor()`)
              }

  

  ngOnInit(): void {
    this.chatPanelSubscription = this.chatService.updateChatPanelEmmiter.subscribe(
      (a) => {
        console.log('ChatPanelComponent.chatPanelSubscription -> getting chat data via updateChatPanelEmmiter')
        this.chatData = this.chatService.getCurrentChatData()
        // added
        if ( this.firstLoad )  {
          // this.htmlService.scrollDown( true )
          //this.htmlService.resizeMessageListImmediately()
          this.firstLoad = false
        } 
        this.htmlService.resizeMessageListImmediately()
        /* else {
          
        } */
      }
    )

    this.initalizationSubscription = this.connectionService.serviceInitializedEmitter.subscribe(
      (n) => {
        console.log('ChatPanelComponent.initalizationSubscription -> getting chat data via serviceInitializedEmitter')
        this.chatData = this.chatService.getCurrentChatData()
        // if chat is not found we nned to redirect to page-not found
        if ( this.chatData ) {
          this.chatService.selectChat( this.chatData.chat.chatId )
          
          // this.htmlService.scrollDown( true ) // AAAAAAAAAAAAAAAAAAAAAAAAAA
        }
        else {
          this.router.navigate(['page-not-found']);
        } 
        
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


    /*
      Problem
      problem z podwójnymi wiadomościami pojawia się jak mamy już otwarty czat i wchodzimy do innego 
      chatu poprzez chat-list
    */


    this.messageListScrollingSubscription = this.htmlService.messageListScrollEventEmitter.subscribe(
      (position) => {
        console.log('position', position)
        if (position == 'down') {
          if (this.chatData) {
            // nowe 
            this.chatService.markMessagesAsRead( this.chatData.chat.chatId ) 
            // ta metoda powinna sama wysyłać event o aktualizację offsetu do backendu przez WS (zrobić to emiterem), 
            // ale tylko jeśli liczba nieprzeczytanych wiadomości jest >0, 
            
            // natomiast samo zjechanie na dół powinno updejtować sesję niezależnie od liczby nieprzeczytanych wiadomości.
            this.htmlService.scrollDown( false )
          }
        }
        if (position == 'top' ) { // && ! this.firstLoad
          if (this.chatData) {
            console.log('fetching older messages')
            this.chatService.fetchOlderMessages( this.chatData.chat.chatId )
          }
        }
      } 
    )  



  


    /*
      problem jest taki , że jak chatPanel się inicjuje to nie ma jeszcze czatów, dlatego selectedChat jest undefined
      natomiast jak czaty zostaną pobrane to 

      to się wykonuje zanim dane o chatach userze i settings zostaną zassane z servera
    */



    const chatId = this.activated.snapshot.paramMap.get('chatId');
    if ( chatId ) { 
      console.log('ChatPanelComponent.ngOnInit() -> chatId: ', chatId)
      this.chatService.setChatId( chatId )
      // we need to change selected chatId only when current chatId is other than selected


      // this.chatService.selectChat( chatId ) // required if we load page from webbrowser,
      
      this.chatData =  this.chatService.getCurrentChatData()
      if (this.chatData) console.error('is not empty')

      // new
      // this.htmlService.resizeMessageListImmediately()

      
      // old
      // this.chatService.fetchOlderMessages( chatId ) // dodałem na samym końcu
      // not chat list in web application
    } else {
      console.error('ChatPanelComponent.ngOnInit() -> no chatId in path !!!')
    }

  }




  ngOnDestroy(): void {
    console.log(`ChatPanelComponent.ngOnDestroy()`)
    if ( this.chatPanelSubscription )            this.chatPanelSubscription.unsubscribe()
    if ( this.receivingWritingSubscription )     this.receivingWritingSubscription.unsubscribe()
    if ( this.messageListScrollingSubscription ) this.messageListScrollingSubscription.unsubscribe()
    if ( this.initalizationSubscription )        this.initalizationSubscription.unsubscribe()
    this.chatService.clearSelectedChat()
  }




  /*
  here we get message send via emmiter from subcomponent 
  */
  sendMessage(m: Message) {
    console.log(`ChatPanelComponent.sendMessage() -> sending message: `, m)
    this.connectionService.sendMessage( m )
  }




  goToChatSettings() {
    console.log(`ChatPanelComponent.goToChatSettings()`)
    this.connectionService.updateSession()
    this.router.navigate(['user', 'editChat', `${this.chatData?.chat.chatId}`])
  }



}

import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatData } from 'src/app/models/ChatData';
import { Message } from 'src/app/models/Message';
import { Writing } from 'src/app/models/Writing';
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { HtmlService } from 'src/app/services/html.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit, OnDestroy {

  wrt:                              Writing      | undefined
  chatData:                         ChatData     | undefined
  fetchingSubscription:             Subscription | undefined
  writingSubscription:              Subscription | undefined
  selectedChatSubscription:         Subscription | undefined
  chatModificationSubscription:     Subscription | undefined
  messageListScrollingSubscription: Subscription | undefined




  constructor(private userService: UserService, 
              private htmlService: HtmlService, 
              private chatService: ChatsDataService,
              private router: Router,
              private activated: ActivatedRoute) { }

  

  ngOnInit(): void {
    console.log('ChatPanelComponent.ngOnInit')


    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (c) => {
        if (c == 1 || c == 3 || c == 4) { 
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) { 
            console.log('ChatPanelComponent fetchingSubscription fetched data from UserService via fetchEmmiter.')
            const currentChat = this.userService.getAllChats().find(  (chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            })
            if ( currentChat ) {
              const unreadMessLength = currentChat.unreadMessages.length
              if (unreadMessLength > 0){ 
                if ( this.htmlService.isScrolledDown() == 1 ) { 
                  console.warn('New messages and scrolled DOWN')
                  this.chatData = this.userService.markMessagesAsRead( chatId )
                  this.userService.dataFetched( 2 ) // refresh chat list
                  this.htmlService.scrollDown( false )
                  if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
                  if ( this.chatData ) { // this subscription is only to reassign older messages
                    this.chatModificationSubscription = this.chatData.emitter.subscribe(
                      (cd) => this.chatData = cd
                    )  
                  }                  
                } else {
                  this.chatData = currentChat // this chat data has unread messages 
                }
              } else { // no new messages
                this.chatData = currentChat
                this.userService.markMessagesAsRead( this.chatData.chat.chatId )
                this.htmlService.scrollDown( false )
                console.warn('no new messages')
              }
            } else {
              console.log('ChatPanelComponent.ngOnInit() fetchingSubscription chat not found in list.')
              this.chatService.selectChat( undefined )
              this.router.navigate(['page-not-found']);
            }            
          } 
        }
      }
    );

    // this subscription is called when user select another chat. from list.
    this.selectedChatSubscription = this.userService.selectedChatEmitter.subscribe( 
      (cd) =>  {
        // we modify only when new chat is selected
        if (cd.chat.chatId != this.chatData?.chat.chatId) {
          // switch off subscription of prewous chat
          if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
          this.chatData = this.userService.markMessagesAsRead( cd.chat.chatId )
          if ( this.chatData ) {
            this.htmlService.resizeMessageListImmediately()
            this.htmlService.scrollDown( false )
            // tutaj trzeba wymusić resize chat listy do rozmiarów pasujących do okna. 
            this.chatModificationSubscription = this.chatData.emitter.subscribe(
              (cd) => { this.chatData = cd }
            )              
          } else {
            console.log('ChatPanelComponent.ngOnInit() selectedChatSubscription chat not found in list.')
            this.router.navigate(['page-not-found']);
          }
        }
      }
    )   


    // we need to stay it because cannot insert Writing value via html. 
    this.writingSubscription = this.userService.getWritingEmmiter().subscribe(
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
            const unreadMessLength = this.chatData.unreadMessages.length
            if (unreadMessLength > 0){ 
              this.chatData = this.userService.markMessagesAsRead( this.chatData.chat.chatId )
              this.userService.dataFetched( 2 ) // refresh chat list
              this.htmlService.scrollDown( false )
              if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
              if ( this.chatData ) { // this subscription is only to reassign older messages
                this.chatModificationSubscription = this.chatData.emitter.subscribe(
                  (cd) => { 
                    console.warn('Adding older messages.')
                    this.chatData = cd
                  }
                )  
              } 
            }
          }
        }
        if (position == 'top') {
          if (this.chatData) {
            console.log('fetching older messages')
            this.userService.fetchOlderMessages( this.chatData.chat.chatId )
          }
        }
      } 
    )    


    const chatId = this.activated.snapshot.paramMap.get('chatId');
    if ( chatId ) { this.chatService.selectChat( chatId )}
    

    
    if ( this.userService.isWSconnected() ) {
      this.userService.dataFetched( 4 ) // here we need to only fetch to chat panel. 
    } 
    
  }




  ngOnDestroy(): void {
    if ( this.chatModificationSubscription )  this.chatModificationSubscription.unsubscribe()
    if ( this.fetchingSubscription )          this.fetchingSubscription.unsubscribe()
    if ( this.writingSubscription )           this.writingSubscription.unsubscribe()
    if ( this.selectedChatSubscription )      this.selectedChatSubscription.unsubscribe()
    if ( this.messageListScrollingSubscription ) this.messageListScrollingSubscription.unsubscribe()
    this.userService.selectChat( undefined )
  }




  sendMessage(m: Message) {
    console.log(`sending message: ${m}`, m)
    this.userService.updateSession(true)
    this.userService.sendMessage( m )                        
  }




  goToChatSettings() {
    this.userService.updateSession(true)
    this.router.navigate(['user', 'editChat', `${this.chatData?.chat.chatId}`])
  }





}

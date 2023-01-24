import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatData } from 'src/app/models/ChatData';
import { ChatOffsetUpdate } from 'src/app/models/ChatOffsetUpdate';
import { Message } from 'src/app/models/Message';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit, OnDestroy {

  chatData:                     ChatData     | undefined;
  fetchingSubscription:         Subscription | undefined
  selectedChatSubscription:     Subscription | undefined
  chatModificationSubscription: Subscription | undefined
  errorBody:                    any          | undefined


  constructor(private userService: UserService, private router: Router, private activated: ActivatedRoute) { }


  /*
  Dwa scenariusze uruchomienia:
  1. użytkownik wchodzi w chat panel z chat-list wybierając konkretny chat. 
     ngOnInit() jest wtedy uruchamiane i onClick() też.

  2. użytkownik wchodzi bezpośrednio na stronę poprzez refresh strony 
     ngOnInit() jest wtedy uruchamiany ale onClick() w chat-list już nie jest wywoływany. 
  */

  ngOnInit(): void {
    console.log('ChatPanelComponent.ngOnInit')
    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if (b) {
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) { // && (chatId != this.chatData?.chat.chatId)
            console.log('ChatPanelComponent fetchingSubscription fetched data from UserService via fetchEmmiter.')
            const found = this.userService.getAllChats().find((chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            })
            if ( found ) {
              const length = found.unreadMessages.length
              this.userService.markMessagesAsRead( chatId )
              this.chatData = this.userService.getAllChats().find((cd2, index, arr) => {
                return cd2.chat.chatId == chatId;
              })
              if (this.chatData) {
                if (this.userService.user?.userId) {
                  const chatOffsetUpdate: ChatOffsetUpdate = {
                    userId:           this.userService.user.userId,
                    chatId:           this.chatData.chat.chatId,
                    lastMessageTime:  this.chatData.chat.lastMessageTime,
                    partitionOffsets: this.chatData.partitionOffsets 
                  }
                  this.userService.sendChatOffsetUpdate( chatOffsetUpdate )
                }
                if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
                this.chatModificationSubscription = this.chatData.emitter.subscribe(
                  (cd) => this.chatData = cd
                )              
              } 
            } else {
              console.log('ChatPanelComponent.ngOnInit() fetchingSubscription chat not found in list.')
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
          this.userService.markMessagesAsRead( cd.chat.chatId )
          console.log('ChatPanelComponent selectedChatSubscription fetched data from UserService.')
          if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
          this.chatData = this.userService.getAllChats().find((cd2, index, arr) => {
            return cd2.chat.chatId == cd.chat.chatId;
          })
          if (this.chatData) {
            if (this.userService.user?.userId) {
              const chatOffsetUpdate: ChatOffsetUpdate = {
                userId:           this.userService.user.userId,
                chatId:           this.chatData.chat.chatId,
                lastMessageTime:  this.chatData.chat.lastMessageTime,
                partitionOffsets: this.chatData.partitionOffsets 
              }
              this.userService.sendChatOffsetUpdate( chatOffsetUpdate )
            }
            this.chatModificationSubscription = this.chatData.emitter.subscribe(
              (cd) => this.chatData = cd
            )              
          } else {
            console.log('ChatPanelComponent.ngOnInit() selectedChatSubscription chat not found in list.')
            this.router.navigate(['page-not-found']);
          }
        }
      }
    )   
    // const chatId = this.activated.snapshot.paramMap.get('chatId');
    // if ( chatId ) this.userService.markMessagesAsRead( chatId )
    if ( this.userService.isWSconnected() ) this.userService.dataFetched() 
  }




  ngOnDestroy(): void {
    if ( this.chatModificationSubscription ) this.chatModificationSubscription.unsubscribe()
    if ( this.fetchingSubscription )         this.fetchingSubscription.unsubscribe()
    if ( this.selectedChatSubscription )     this.selectedChatSubscription.unsubscribe()
    this.userService.selectChat( undefined )
  }




  sendMessage(m: Message) {
    console.log('sending message', m)
    this.userService.updateSession()
    this.userService.sendMessage( m )                        
  }




  goToChatSettings() {
    this.userService.updateSession()
    this.router.navigate(['user', 'editChat', `${this.chatData?.chat.chatId}`])
  }


}

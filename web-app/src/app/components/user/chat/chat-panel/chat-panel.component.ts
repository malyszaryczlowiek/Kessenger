import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatData } from 'src/app/models/ChatData';
import { Message } from 'src/app/models/Message';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit, OnDestroy {

  chatData: ChatData | undefined;
  fetchingSubscription: Subscription | undefined
  selectedChatSubscription: Subscription | undefined
  chatModificationSubscription: Subscription | undefined
  errorBody: any | undefined


  constructor(private userService: UserService, private router: Router, private activated: ActivatedRoute) { }


  ngOnInit(): void {
    console.log('ChatPanelComponent.ngOnInit')
    // this subscription called when page is reload 
    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if (b) {
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) { // && (chatId != this.chatData?.chat.chatId)
            console.log('ChatPanelComponent fetchingSubscription fetched data from UserService via fetchEmmiter.')
            this.chatData = this.userService.chatAndUsers.find((chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            })
            if (this.chatData) {
              if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
              this.chatModificationSubscription = this.chatData.emitter.subscribe(
                (cd) => this.chatData = cd
              )              
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
          console.log('ChatPanelComponent selectedChatSubscription fetched data from UserService.')
          if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
          this.chatData = this.userService.chatAndUsers.find((chatData, index, arr) => {
            return chatData.chat.chatId == cd.chat.chatId;
          })
          if (this.chatData) {
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
    this.userService.dataFetched() // moved to on complete
  }

  ngOnDestroy(): void {
    if ( this.chatModificationSubscription ) this.chatModificationSubscription.unsubscribe()
    if ( this.fetchingSubscription )         this.fetchingSubscription.unsubscribe()
    if ( this.selectedChatSubscription )     this.selectedChatSubscription.unsubscribe()
  }


  sendMessage(m: Message) {
    console.log('sending message', m)
    this.userService.updateSession()
    this.userService.sendMessage( m )                         // todo continue here
  }



  goToChatSettings() {
    this.userService.updateSession()
    this.router.navigate(['user', 'editChat', `${this.chatData?.chat.chatId}`])
  }


}

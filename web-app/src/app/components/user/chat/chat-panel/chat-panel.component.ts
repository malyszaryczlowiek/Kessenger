import { Component, EventEmitter, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatData } from 'src/app/models/ChatData';
import { ChatOffsetUpdate } from 'src/app/models/ChatOffsetUpdate';
import { Message } from 'src/app/models/Message';
import { Writing } from 'src/app/models/Writing';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit, OnDestroy {

  chatData:                     ChatData     | undefined
  fetchingSubscription:         Subscription | undefined
  writingSubscription:          Subscription | undefined
  selectedChatSubscription:     Subscription | undefined
  chatModificationSubscription: Subscription | undefined
  //messageListSubscription:      Subscription | undefined

  errorBody:                    any          | undefined
  wrt:                          Writing      | undefined

  rescaleAndScrollTimer:      NodeJS.Timeout | undefined;

  //firstLoad = true

  // full - rescale and scroll
  // scroll - 
  // rescale - 
  messageListEmmiter: EventEmitter<string> = new EventEmitter<string>()

  


  constructor(private userService: UserService, private router: Router, private activated: ActivatedRoute) { }


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
              this.chatData = this.userService.getAllChats().find( (cd2, index, arr) => {
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
                  if ( ! this.rescaleAndScrollTimer ) {
                    this.rescaleAndScrollTimer = setInterval(() => {
                      this.setHeightLayout()
                      this.scrollDown()
                      console.log('interval')
                    }, 700)
                  }
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
              this.scrollDown()
            }
            this.chatModificationSubscription = this.chatData.emitter.subscribe(
              (cd) => {
                this.chatData = cd
                // here // tutaj jeszcze dodać, że jak przychodzi nowa wiadomość to jeśli nie jeszteśmy
              // na końcu to należy wyświetlić powiadomienie, że można zjechać na dół
              }
            )              
          } else {
            console.log('ChatPanelComponent.ngOnInit() selectedChatSubscription chat not found in list.')
            this.router.navigate(['page-not-found']);
          }
        }
      }
    )   

    todo33 // to zlikwidować i brać z ChatComponent
    this.writingSubscription = this.userService.getWritingEmmiter().subscribe(
      (w: Writing | undefined) => { this.wrt = w }
    )

    window.addEventListener("resize" , (event) => {
      this.setHeightLayout()
      this.scrollDown()
    })

    if ( this.userService.isWSconnected() ) this.userService.dataFetched() 
    
  }




  ngOnDestroy(): void {
    if ( this.chatModificationSubscription ) this.chatModificationSubscription.unsubscribe()
    if ( this.fetchingSubscription )         this.fetchingSubscription.unsubscribe()
    if ( this.writingSubscription )          this.writingSubscription.unsubscribe()
    if ( this.selectedChatSubscription )     this.selectedChatSubscription.unsubscribe()
    // if ( this.messageListSubscription )      this.messageListSubscription.unsubscribe()
    if ( this.rescaleAndScrollTimer )        clearTimeout( this.rescaleAndScrollTimer )
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





  // layout methods

  setHeightLayout(){
    const header = document.getElementById('header')
    const chatHeader = document.getElementById('chat_header')
    const messageList = document.getElementById('messages')
    const sendMessage = document.getElementById('send_message')
    if ( messageList && chatHeader && header && sendMessage ) {
      const h = window.innerHeight - 
        header.offsetHeight -
        chatHeader.offsetHeight - 
        sendMessage.offsetHeight 
      messageList.style.height = h + 'px'
    } else {
      console.log('header', header)
      console.log('chatHeader', chatHeader)
      console.log('messageList', messageList)
      console.log('sendMessage', sendMessage)
    }
  }

  scrollDown() {
    const messages = document.getElementById('messages')
    if (messages) {
      messages.scrollTo(0, messages.scrollHeight)
      if( this.rescaleAndScrollTimer ) clearInterval( this.rescaleAndScrollTimer )

      todo
      // 1. sprawdzić co się da przenieść do chat panelu
      // 2. wysyłanie info o updejcie chat offsetu powinno być wysyłane tylko wtedy gdy unread messages jest > 0
      // 3. napisać Html service do zarządzania rescalowaniem, scrollowaniem i przesuwaniem focusu
      // 4. napisać scroll listenera, który będzie wysyłał żądanie o wcześniejsze wiadomości
      // 5. napsać sprawdzanie czy jak przychodzi nowa wiadomość to czy wyświetlić zapyutanie o przeskrolowanie do dołu
      // i jeśli  przyjdzie wiadomość to trzeba sprawdzić czy to położenie 
      // jest mniejsze niż maksymalny scroll minus wysokość ostatniej wiadomości 
      // jeśli jest większe to należy wywołąć jeszcze scrollDown tak aby po przyjściu wiadomosci 
      // zamiast wyświetlać komunikat przejść do końca lini. 
      
    }
  }

}

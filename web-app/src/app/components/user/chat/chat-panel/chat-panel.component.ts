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



  // fetchingSubscription:             Subscription | undefined
  // selectedChatSubscription:         Subscription | undefined
  // chatModificationSubscription:     Subscription | undefined // to chyba będzie można usunąć


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
        if ( this.connectionService.isInitlized() && ! this.chatData ) {
          console.log('ChatPanelComponent.chatPanelSubscription  chatId from path not found in list of chats.')
          this.router.navigate(['page-not-found']);
        }
      }
    )


/*     this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (c) => {
        if (c == 1 || c == 3 || c == 4) { 
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) { 
            console.log('ChatPanelComponent fetchingSubscription fetched data from UserService via fetchEmmiter.')
            const currentChat = this.userService.getAllChats().find( (chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            })
            if ( currentChat ) {
              const unreadMessLength = currentChat.unreadMessages.length
              if (unreadMessLength > 0){ 
                if ( this.htmlService.isScrolledDown() == 1 ) { 
                  console.warn('New messages and scrolled DOWN')

                  // error

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
                
                //error
                
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
 */






    // this subscription is called when user select another chat. from list.
/*     this.selectedChatSubscription = this.userService.selectedChatEmitter.subscribe( 
      (cd) =>  {
        // we modify only when new chat is selected
        if (cd.chat.chatId != this.chatData?.chat.chatId) {
          // switch off subscription of previous chat
          if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()

          // error
          
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
 */

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


            // stare 

            // const unreadMessLength = this.chatData.unreadMessages.length
            // if (unreadMessLength > 0){ 

              // error
              
              /* this.chatData = this.userService.markMessagesAsRead( this.chatData.chat.chatId )
              this.userService.dataFetched( 2 ) // refresh chat list
              this.htmlService.scrollDown( false )
              if (this.chatModificationSubscription) this.chatModificationSubscription.unsubscribe()
              if ( this.chatData ) { // this subscription is only to reassign older messages
                this.chatModificationSubscription = this.chatData.emitter.subscribe(
                  (cd) => { 
                    console.log('Adding older messages.')
                    this.chatData = cd
                  }
                )  
              }  */
            // }
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
      // not chat list in web application

      // old
      //  this.chatService.selectChat( chatId ) // zakomentowałem bo selekcja była już na etapie wyboru z listy
      // this.chatService.fetchOlderMessages( chatId )
      
      //  zakomentowałem bo fetchowanie starych wiadomości powinno zrobić update chat-panelu z wczytaniem wiadomośći włąćznie
      // this.chatData = this.chatService.getCurrentChatData() // added
    }
    

    // zakomentowuje
    /* if ( this.userService.isWSconnected() ) {
      this.userService.dataFetched( 4 ) // here we need to only fetch to chat panel. 
    }  */
    
  }




  ngOnDestroy(): void {
    if ( this.chatPanelSubscription )            this.chatPanelSubscription.unsubscribe()
    if ( this.receivingWritingSubscription )     this.receivingWritingSubscription.unsubscribe()
    if ( this.messageListScrollingSubscription ) this.messageListScrollingSubscription.unsubscribe()
    this.chatService.clearSelectedChat()
    
    // if ( this.selectedChatSubscription )         this.selectedChatSubscription.unsubscribe()
    // if ( this.chatModificationSubscription )     this.chatModificationSubscription.unsubscribe()
    // if ( this.fetchingSubscription )             this.fetchingSubscription.unsubscribe()
    // this.userService.selectChat( undefined )
  }




  sendMessage(m: Message) {
    console.log(`sending message: ${m}`, m)
    // this.userService.updateSession(true) // sprawdzić czy updejtuje sesję metoda sendMessage
    this.chatService.sendMessage( m )                        
  }




  goToChatSettings() {
    // this.userService.updateSession(true) // sprawdzić czy updejtuje sesję metoda sendMessage albo ngOnInit() dla componentu
    this.router.navigate(['user', 'editChat', `${this.chatData?.chat.chatId}`])
  }



}

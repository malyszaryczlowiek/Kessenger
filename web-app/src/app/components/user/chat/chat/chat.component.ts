import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
// serivces
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { ConnectionService } from 'src/app/services/connection.service';
import { ResponseNotifierService } from 'src/app/services/response-notifier.service';
// models
import { ChatData } from 'src/app/models/ChatData';



@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.css']
})
export class ChatComponent implements OnInit, OnDestroy {

  // chats: ChatData[] = new Array<ChatData>()
  chatListSubscription:             Subscription | undefined
  // subscribe initalization finished
  initalizationSubscription:        Subscription | undefined
  
  
  constructor(private chatService: ChatsDataService,   
      private connectionService: ConnectionService, 
      private responseNotifier: ResponseNotifierService) { }



  ngOnInit(): void {
    console.log('ChatComponent.ngOnInit()')
    /* this.chatListSubscription = this.chatService.updateChatListEmmiter.subscribe(
      (n) => {
        console.log('ChatComponent.chatListSubscription -> fetched chat list from ChatDataService via updateChatListEmmiter ')
        this.chats = this.chatService.chatAndUsers
      }
    )

    this.initalizationSubscription = this.connectionService.serviceInitializedEmitter.subscribe(
      (n) => {
        console.log('ChatComponent.initalizationSubscription -> fetched chat list from ChatDataService via serviceInitializedEmitter ')
        this.chats = this.chatService.chatAndUsers
      }
    )

    this.chatService.updateChatList() */

    /* const userId = this.chatService.user?.userId
    if ( this.chatService.chatAndUsers.length == 0 && userId ) {
      const c = this.connectionService.getChats( userId )
      if ( c ) {
        console.log('ChatComponent.ngOnInit() Reading chats from server...')
        c.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const body = response.body              
              if (body) {
                this.chatService.setChats( body )
                // if we do not have WS connection, we try to connect
                // if ( this.connectionService.isWSconnected() ) this.connectionService.connectViaWebsocket()
                // changed above to below command. we need to connect only when connection is undefined,
                // otherwise connection may by in 'connecting' state. 
                if ( this.connectionService.isWSconnectionDefined() ) this.connectionService.connectViaWebsocket()
                // old
                //this.userService.setChats( body )
                //this.userService.connectViaWebsocket() // run websocket connection
              }
              else console.log('ChatComponent.ngOnInit() empty body')
            }
            else console.log(`getChat() got status ${response.status}`)
          },
          error: (e) => {
            this.responseNotifier.handleError( e )
          },
          complete: () => {}
        })
      } else {
        console.log('ChatComponent.ngOnInit() cannot send request to server for chats, Invalid session ???')
      }
    } else {
      console.log('ChatComponent.constructor() chat data read from UserService directly')
      this.chats = this.chatService.chatAndUsers
    } */


  }



  ngOnDestroy() {
    if ( this.chatListSubscription )      this.chatListSubscription.unsubscribe()
    if ( this.initalizationSubscription ) this.initalizationSubscription.unsubscribe()
    console.log('ChatComponent.ngOnDestroy()')
  }



}

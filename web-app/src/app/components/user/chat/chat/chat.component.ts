import { Component, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.css']
})
export class ChatComponent implements OnInit, OnDestroy {

  chats: ChatData[] = new Array<ChatData>()
  fetchingSubscription: Subscription | undefined
  responseError: any | undefined


  constructor(private userService: UserService) { }

  ngOnInit(): void {
    // tutaj // sprawdzić czy nie można zostawić samego fetchowania danych przez poniższy emmiter. 
    // i tylko użyć if (this.userService.isWSconnected() ) this.userService.dataFetched()
    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe( (c) => {
        if (c == 1 || c == 2) { 
          console.log('ChatComponent fetched data from UserService via fetchEmmiter.')
          this.chats = this.userService.getAllChats()
        }
      }
    )

    if ( this.userService.getAllChats().length == 0 ) {
      const c = this.userService.getChats()
      if ( c ) {
        console.log('ChatComponent.ngOnInit() Reading chats from server...')
        c.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const body = response.body              
              if (body) {
                this.userService.setChats( body )
                this.userService.connectViaWebsocket() // run websocket connection
              }
              else console.log('ChatComponent.ngOnInit() empty body')
            }
            else console.log(`getChat() got status ${response.status}`)
          },
          error: (e) => {
            this.responseError = e.error
          },
          complete: () => {}
        })
      } else {
        console.log('ChatComponent.ngOnInit() cannot send request to server for chats, Invalid session ???')
      }
    } else {
      console.log('ChatComponent.constructor() chat data read from UserService directly')
      this.chats = this.userService.getAllChats()
    }
  }



  ngOnDestroy() {
    if ( this.fetchingSubscription ) this.fetchingSubscription.unsubscribe()
    console.log('ChatComponent.ngOnDestroy()')
  }



  clearError() {
    this.responseError = undefined
    this.userService.updateSession()
  }

}

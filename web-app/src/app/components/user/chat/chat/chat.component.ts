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
    this.fetchingSubscription = this.userService.fetchingUserDataFinishedEmmiter.subscribe( (b) => {
        if (b) {
          console.log('ChatComponent fetched data from UserService via fetchEmmiter.')
          this.chats = this.userService.chatAndUsers
        }
      }
    )
    if ( this.userService.chatAndUsers.length == 0 ) {
      const c = this.userService.getChats()
      if ( c ) {
        console.log('ChatComponent.ngOnInit() Reading chats from server...')
        c.subscribe({
          next: (response) => {
            if (response.status == 200) {
              const body = response.body
              if (body) {
                console.log('ChatComponent.ngOnInit() chats from server saved.')
                const body2 = body.map(
                  (cd) => {
                    cd.emitter = new EventEmitter<ChatData>()
                    return cd
                  }
                ) 
                this.userService.setChats(body2)
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
      this.chats = this.userService.chatAndUsers
    }
  }



  ngOnDestroy() {
    if (this.fetchingSubscription ) this.fetchingSubscription.unsubscribe()
    console.log('ChatComponent.ngOnDestroy()')
  }



  clearError() {
    this.responseError = undefined
    this.userService.updateSession()
  }

}

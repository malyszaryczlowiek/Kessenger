import { Component, OnDestroy, OnInit } from '@angular/core';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.css']
})
export class ChatComponent implements OnInit, OnDestroy {

  chats: ChatData[] = new Array<ChatData>()
  testString = this.userService.testString
  testObservable = this.userService.testObservable

  fetchDataEmitter = this.userService.fetchingUserDataFinishedEmmiter

  responseError: any | undefined

  constructor(private userService: UserService) { }

  ngOnInit(): void {

    this.fetchDataEmitter.subscribe( (b) => {
        if (b) {
          console.log('ChatComponent fetched data from UserService via fetchEmmiter.')
          this.chats = this.userService.chatAndUsers
        }
      }
    )

    // fech data only when chats in userService is empty
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
                this.userService.setChats(body)
              }
              else {
                console.log('ChatComponent.ngOnInit() empty body')
              }
            }
            else {
              console.log(`getChat() got status ${response.status}`)
            }
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
      this.chats = this.userService.chatAndUsers
    }
  }



  ngOnDestroy() {
    console.log('ChatComponent.ngOnDestroy()')
  }



  clearError() {
    this.responseError = undefined
    this.userService.updateSession()
  }

}

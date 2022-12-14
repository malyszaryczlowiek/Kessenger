import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { Message } from 'src/app/models/Message';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.component.html',
  styleUrls: ['./chat-panel.component.css']
})
export class ChatPanelComponent implements OnInit {

  constructor(private userService: UserService, private router: Router, private activated: ActivatedRoute) { }

  public chatData: ChatData | undefined;

  ngOnInit(): void {
    console.log('ChatPanelComponent.ngOnInit')

    this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if (b) {
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) {
            this.chatData = this.userService.chatAndUsers.find((chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            });
            if (this.chatData) {} // ok
            else this.router.navigate(['page-not-found']);
          } else {
            this.router.navigate(['page-not-found']);
          }
        }
      }
    )

    this.userService.selectedChatEmitter.subscribe(
      (cd) => this.chatData = cd
    )

    this.userService.dataFetched()
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

import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Message } from 'src/app/models/Message';
import { ConnectionService } from 'src/app/services/connection.service';
import { HtmlService } from 'src/app/services/html.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-message-list',
  templateUrl: './message-list.component.html',
  styleUrls: ['./message-list.component.css']
})
export class MessageListComponent implements OnInit, OnDestroy {


  @Input() messages: Message[] = new Array<Message>();
  messageListScrollSubscription: Subscription | undefined

  constructor(private connectionService: ConnectionService, private htmlService: HtmlService) { } // private userService: UserService
  

  ngOnInit(): void {
    this.htmlService.startMessageListScrollListener()
    this.messageListScrollSubscription = this.htmlService.messageListScrollEventEmitter.subscribe(
      (s) => {
        if (s == 'top') {
          console.log('MessageListComponent.messageListScrollSubscription : scrolled to top')
        }
        if (s == 'down') {
          console.log('MessageListComponent.messageListScrollSubscription : scrolled to down')
        }          
        this.connectionService.updateSession()
      }
    )
    this.htmlService.resizeMessageListAfter(100)
  }


  ngOnDestroy(): void {
    this.htmlService.stopScrollEmmiter()
    if ( this.messageListScrollSubscription ) this.messageListScrollSubscription.unsubscribe()
  }

}

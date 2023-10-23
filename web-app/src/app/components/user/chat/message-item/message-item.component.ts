import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
// services
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UtctimeService } from 'src/app/services/utctime.service';
import { HtmlService } from 'src/app/services/html.service';
// models
import { Message } from 'src/app/models/Message';



@Component({
  selector: 'app-message-item',
  templateUrl: './message-item.component.html',
  styleUrls: ['./message-item.component.css']
})
export class MessageItemComponent implements OnInit, OnDestroy {

  constructor(private utc: UtctimeService, private chatService: ChatsDataService, 
    private htmlService: HtmlService,
    private userSettings: UserSettingsService) { }

  @Input() message: Message | undefined;
  me: boolean | undefined

  messageListScrollingSubscription: Subscription | undefined

  ngOnInit(): void {
    this.me = this.message?.authorId == this.chatService.user?.userId

//     this.htmlService.scrollDown( true )


 
  }

  ngOnDestroy(): void {
    if ( this.messageListScrollingSubscription ) this.messageListScrollingSubscription.unsubscribe()
  }

  parseDate(millis: number): string {
    return this.utc.getDate(millis, this.userSettings.settings.zoneId)
  }


}

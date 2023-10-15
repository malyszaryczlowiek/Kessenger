import { Component, Input, OnInit } from '@angular/core';
// services
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UtctimeService } from 'src/app/services/utctime.service';
// models
import { Message } from 'src/app/models/Message';



@Component({
  selector: 'app-message-item',
  templateUrl: './message-item.component.html',
  styleUrls: ['./message-item.component.css']
})
export class MessageItemComponent implements OnInit {

  constructor(private utc: UtctimeService, private chatService: ChatsDataService, private userSettings: UserSettingsService) { }

  @Input() message: Message | undefined;
  me: boolean | undefined

  ngOnInit(): void {
    this.me = this.message?.authorId == this.chatService.user?.userId
  }

  parseDate(millis: number): string {
    return this.utc.getDate(millis, this.userSettings.settings.zoneId)
  }


}

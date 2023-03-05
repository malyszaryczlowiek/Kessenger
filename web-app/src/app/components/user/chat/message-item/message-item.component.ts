import { Component, Input, OnInit } from '@angular/core';
import { Message } from 'src/app/models/Message';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';
import { UtctimeService } from 'src/app/services/utctime.service';

@Component({
  selector: 'app-message-item',
  templateUrl: './message-item.component.html',
  styleUrls: ['./message-item.component.css']
})
export class MessageItemComponent implements OnInit {

  constructor(private utc: UtctimeService, private userService: UserService, private userSettings: UserSettingsService) { }

  @Input() message: Message | undefined;
  me: boolean | undefined

  ngOnInit(): void {
    this.me = this.message?.authorId == this.userService.user?.userId
  }

  parseDate(millis: number): string {
    return this.utc.getDate(millis, this.userSettings.settings.zoneId)
  }


}

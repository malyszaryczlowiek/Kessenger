import { Component, Input, OnInit } from '@angular/core';
import { ChatData } from 'src/app/models/ChatData';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UtctimeService } from 'src/app/services/utctime.service';

@Component({
  selector: 'app-chat-list-item',
  templateUrl: './chat-list-item.component.html',
  styleUrls: ['./chat-list-item.component.css']
})
export class ChatListItemComponent implements OnInit {

  @Input() chatData?: ChatData;

  constructor(private userSettingsService: UserSettingsService, private utc: UtctimeService) { }

  ngOnInit(): void {
  }

  calculateDate(n: number) {
    return this.utc.getDate(n, this.userSettingsService.settings.zoneId)
  }



}

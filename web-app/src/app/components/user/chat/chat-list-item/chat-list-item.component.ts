import { Component, Input, OnInit } from '@angular/core';
import { ChatData } from 'src/app/models/ChatData';
import { Writing } from 'src/app/models/Writing';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';
import { UtctimeService } from 'src/app/services/utctime.service';

@Component({
  selector: 'app-chat-list-item',
  templateUrl: './chat-list-item.component.html',
  styleUrls: ['./chat-list-item.component.css']
})
export class ChatListItemComponent implements OnInit {

  @Input() chatData?: ChatData;
  @Input() wrt:       Writing | undefined


  constructor(private userService: UserService, private userSettingsService: UserSettingsService, private utc: UtctimeService) {}

  ngOnInit(): void {
  }

  calculateDate(n: number) {
    return this.utc.getDate(n, this.userSettingsService.settings.zoneId)
  }

  print(w: Writing | undefined): boolean {
    if (w && this.chatData) return w.chatId == this.chatData.chat.chatId
    else return false
  }

  getNumOfUnreadMessages(): number {
    const userId = this.userService.user?.userId
    if (this.chatData && userId) {
      let num = 0
      this.chatData.unreadMessages.forEach((m,i,arr)=>{
        if (m.authorId != userId) num = num + 1
      })
      return num
    }
    return 0
  }

}

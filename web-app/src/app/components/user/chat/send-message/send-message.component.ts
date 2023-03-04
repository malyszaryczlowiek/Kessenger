import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Chat } from 'src/app/models/Chat';
import { Message } from 'src/app/models/Message';
import { Writing } from 'src/app/models/Writing';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';
import { UtctimeService } from 'src/app/services/utctime.service';

@Component({
  selector: 'app-send-message',
  templateUrl: './send-message.component.html',
  styleUrls: ['./send-message.component.css']
})
export class SendMessageComponent implements OnInit {

  
  @Input() chat: Chat | undefined;
  @Output() sendingMessage: EventEmitter<Message> = new EventEmitter<Message>()


  messageForm = new FormGroup({
    messageContent: new FormControl('', [Validators.required])
  });



  constructor(private userService: UserService, private utc: UtctimeService, private settings: UserSettingsService) { }

  ngOnInit(): void {
    console.log('SendMessageComponent.ngOnInit()')
  }

  onWriting() {
    const me = this.userService.user
    if ( me && this.chat ) {
      const w : Writing = {
        chatId:    this.chat.chatId,
        writerId:    me.userId,
        writerLogin: me.login        
      }
      this.userService.sendWriting( w )
    }
  }

  onSubmit() {
    const messageContent = this.messageForm.controls.messageContent.value
    const user = this.userService.user
    const settings = this.settings.settings
    if (messageContent && this.chat && user) {
      const m: Message = {
        content: messageContent,
        authorId: user.userId,
        authorLogin: user.login,
        sendingTime: this.utc.getUTCmilliSeconds(),
        serverTime: 0,
        zoneId: settings.zoneId,
        chatId: this.chat.chatId,
        chatName: this.chat.chatName,
        groupChat: this.chat.groupChat,
        partOff: {partition: -1, offset: -1}
      }
      this.sendingMessage.emit( m );
      this.messageForm.controls.messageContent.setValue('')
      this.userService.updateSession(true)
    } 
  }

}

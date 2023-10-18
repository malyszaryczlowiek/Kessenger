import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Chat } from 'src/app/models/Chat';
import { Message } from 'src/app/models/Message';
import { Writing } from 'src/app/models/Writing';
import { ChatsDataService } from 'src/app/services/chats-data.service';
import { ConnectionService } from 'src/app/services/connection.service';
import { UserSettingsService } from 'src/app/services/user-settings.service';
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



  constructor( private connectionService: ConnectionService,
    private chatService: ChatsDataService,
    private utc: UtctimeService, 
    private settings: UserSettingsService) { }



  ngOnInit(): void {
    console.log('SendMessageComponent.ngOnInit()')
  }


  
  onWriting() {
    const me = this.chatService.user
    if ( me && this.chat ) {
      const w : Writing = {
        chatId:    this.chat.chatId,
        writerId:    me.userId,
        writerLogin: me.login        
      }
      this.connectionService.sendWriting( w )
      // this.userService.sendWriting( w )
    }
  }

  onSubmit() {
    const messageContent = this.messageForm.controls.messageContent.value
    const user = this.chatService.user
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
      // w componencie nadrzędnym używamy  (sendingMessage)="sendMessage($event)" co oznacza,
      // że event tutaj emitowany (a właściwie jego wartość) trafia jako wejście do metody sendMessage()
      // w componencie nadrzędnym, Dlatego też tutaj ten event jest oznaczony jako @Output().
      this.sendingMessage.emit( m ); 
      this.messageForm.controls.messageContent.setValue('')
      this.connectionService.updateSession() 
      // this.userService.updateSession(true)  
    } 
  }

}

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Chat } from 'src/app/models/Chat';
import { Message } from 'src/app/models/Message';

@Component({
  selector: 'app-send-message',
  templateUrl: './send-message.component.html',
  styleUrls: ['./send-message.component.css']
})
export class SendMessageComponent implements OnInit {

  constructor() { }

  @Input() chat: Chat | undefined;
  @Output() sendingMessage: EventEmitter<Message> = new EventEmitter<Message>()


  messageForm = new FormGroup({
    messageContent: new FormControl('', [Validators.required])
  });

  ngOnInit(): void {
  }

  onSubmit() {
    const messageContent = this.messageForm.controls.messageContent.value;
    if (messageContent) {
      // TODO tutaj stworzyć obiekt Message, który zostanie wysłany dalej
      // następnie dodać w chat-panel obsługę takiego eventa tak aby został on
      // wysłany przez web socket. 
      this.sendingMessage.emit();
    }
  }

}

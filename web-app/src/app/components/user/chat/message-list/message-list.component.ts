import { Component, Input, OnInit } from '@angular/core';
import { Message } from 'src/app/models/Message';

@Component({
  selector: 'app-message-list',
  templateUrl: './message-list.component.html',
  styleUrls: ['./message-list.component.css']
})
export class MessageListComponent implements OnInit {


  @Input() messages: Message[] = new Array<Message>();


  constructor() { }

  ngOnInit(): void {
  }

}

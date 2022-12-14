import { Component, Input, OnInit } from '@angular/core';
import { ChatData } from 'src/app/models/ChatData';

@Component({
  selector: 'app-chat-list-item',
  templateUrl: './chat-list-item.component.html',
  styleUrls: ['./chat-list-item.component.css']
})
export class ChatListItemComponent implements OnInit {

  @Input() chatData?: ChatData;

  constructor() { }

  ngOnInit(): void {
    // here impelement
  }

}

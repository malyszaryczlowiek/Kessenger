import { Component, Input, OnInit } from '@angular/core';
import { Message } from 'src/app/models/Message';

@Component({
  selector: 'app-message-item',
  templateUrl: './message-item.component.html',
  styleUrls: ['./message-item.component.css']
})
export class MessageItemComponent implements OnInit {

  constructor() { }

  @Input() message: Message | undefined;

  ngOnInit(): void {
  }

}

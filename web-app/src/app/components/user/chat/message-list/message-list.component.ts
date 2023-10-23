import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
// services
import { HtmlService } from 'src/app/services/html.service';
// models
import { Message } from 'src/app/models/Message';



@Component({
  selector: 'app-message-list',
  templateUrl: './message-list.component.html',
  styleUrls: ['./message-list.component.css']
})
export class MessageListComponent implements OnInit, OnDestroy {


  @Input() messages: Message[] = new Array<Message>();

  constructor(private htmlService: HtmlService) { } 
  

  ngOnInit(): void {
    this.htmlService.startMessageListScrollListener()
    this.htmlService.resizeMessageListAfter(100)
    this.htmlService.scrollDown( true )
  }


  ngOnDestroy(): void {
    this.htmlService.stopScrollEmmiter()
  }

}


/*
*/
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { UserSettingsService } from 'src/app/services/user-settings.service';
import { UserService } from 'src/app/services/user.service';
import { UtctimeService } from 'src/app/services/utctime.service';

@Component({
  selector: 'app-chat-list',
  templateUrl: './chat-list.component.html',
  styleUrls: ['./chat-list.component.css']
})
export class ChatListComponent implements OnInit, OnDestroy {

  @Input() chats: Array<ChatData> = new Array<ChatData>(); 
  chats2: Array<{cd:ChatData,date:string}> = new Array<{cd:ChatData,date:string}>();  // nie używane
  // uwaga tutaj ponieważ nie jest to @input to nie jest automatycznie aktualizowany
  



  constructor(private userService: UserService, 
    private userSettingsService: UserSettingsService,
    private utcService: UtctimeService,
    private router: Router, private route: ActivatedRoute) { }
  

  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
    this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if ( b ) {
          this.chats = this.userService.chatAndUsers


          // poniżej nie używane
          this.chats2 = this.userService.chatAndUsers.map( // tu przed mapą się kończyło
            (c,i,arr) => {
              return {
                cd: c,
                date: this.utcService.getDate(c.chat.lastMessageTime, this.userSettingsService.settings.zoneId)
              }
            }
          )
          console.log('List has size ' + this.chats.length)
        }
      }
    )
  }



  onClick(c: ChatData) {
    console.log('navigating to chat' + c.chat.chatName)
    this.router.navigate(['user', 'chat', c.chat.chatId]) 
    this.userService.updateSession()
    this.userService.selectedChatEmitter.emit(c) 
  }




  ngOnDestroy(): void {
    console.log('ChatListComponent.ngOnDestroy() called.')
  }



}

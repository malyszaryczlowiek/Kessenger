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


  constructor(private userService: UserService, private router: Router ) {}
    //private userSettingsService: UserSettingsService,
    //private utcService: UtctimeService,
    //private route: ActivatedRoute

  

  ngOnInit(): void {
    console.log('ChatListComponent.ngOnInit() ')
  }
/*     this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if ( b ) {
          this.chats = this.userService.chatAndUsers
          console.log('List has size ' + this.chats.length)
        }
      }
    )
 */  



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

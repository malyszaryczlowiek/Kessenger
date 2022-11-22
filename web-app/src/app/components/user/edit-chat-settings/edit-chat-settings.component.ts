import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-edit-chat-settings',
  templateUrl: './edit-chat-settings.component.html',
  styleUrls: ['./edit-chat-settings.component.css']
})
export class EditChatSettingsComponent implements OnInit {

  public chatSettings = new FormGroup({
    newChatName: new FormControl(''),
    silent: new FormControl(false) 
  });

  // zmieniamy silent i nazwę czatu
  // oraz ewentualnie opuszczamy chat
  public chatData?: ChatData;
  
  constructor(
    private router: Router, 
    private activatedRoute: ActivatedRoute,
    private userService: UserService) { }



  ngOnInit(): void {
    const chatId = this.activatedRoute.snapshot.paramMap.get('chatId')
    if (chatId) {
      this.chatData = this.userService.chatAndUsers.find( (chatData, index, arr) =>{
        return chatData.chat.chatId == chatId;
      });
      if (this.chatData) {
        // initialize form with data
        this.chatSettings.setValue({
          newChatName : this.chatData.chat.chatName,
          silent : this.chatData.chat.silent
        })
      } else {
        // if chat is not found in chats we need to go to page not found
        this.router.navigate(['pageNotFound'])
      }
    } else {
      // if chat not found we redirect to /user
      this.router.navigate(['pageNotFound']);
    }
  }


  // here we save changed name or silence
  saveChanges() {
    console.log('saveChanges was called.')
    // this.userService.
  }



  // if we do not want change data we can navigate back to chat side
  onCancel() {
    if (this.chatData){
      this.router.navigate(['user', 'chat', `${this.chatData.chat.chatId}`]);
    } else {
      this.router.navigate(['user']);
    }    
  }



  // here we handle request to leave chat. 
  leaveChat() {
    console.log('onDelete was called.')
  }

}

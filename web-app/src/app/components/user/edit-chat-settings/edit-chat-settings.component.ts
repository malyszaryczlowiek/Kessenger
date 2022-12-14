import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Chat } from 'src/app/models/Chat';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-edit-chat-settings',
  templateUrl: './edit-chat-settings.component.html',
  styleUrls: ['./edit-chat-settings.component.css']
})
export class EditChatSettingsComponent implements OnInit {

  chatSettings = new FormGroup({
    newChatName: new FormControl(''),
    silent: new FormControl(false) 
  });

  chatData?: ChatData;

  responseMessage: any | undefined
  
  constructor(
    private router: Router, 
    private activated: ActivatedRoute,
    private userService: UserService) { }



  ngOnInit(): void {
    this.userService.fetchingUserDataFinishedEmmiter.subscribe(
      (b) => {
        if (b) {
          const chatId = this.activated.snapshot.paramMap.get('chatId');
          if ( chatId ) {
            this.chatData = this.userService.chatAndUsers.find((chatData, index, arr) => {
              return chatData.chat.chatId == chatId;
            });
            if (this.chatData) {
              this.chatSettings.controls.silent.setValue(this.chatData.chat.silent)
            } // ok
            else this.router.navigate(['page-not-found']);
          } else {
            this.router.navigate(['page-not-found']);
          }
        }
      }
    )
    this.userService.dataFetched()
  }

  ngOnDelete() {
    console.log('EditChatSettingsComponent.ngOnDelete() called.')
  }
  


  // here we save changed name or silence

  
  saveChanges() {
    
    // console.log(`wartość silent jest ${newSilent} a chat data ${this.chatData}`)
    
    if (this.chatData){
      let body: Chat = this.chatData.chat
      const newName  = this.chatSettings.value.newChatName
      let newSilent: boolean = false 
      if (this.chatSettings.value.silent) newSilent = true
      if (newName) {
        body = {
          chatId:          this.chatData.chat.chatId,
          chatName:        newName,
          groupChat:       this.chatData.chat.groupChat,
          lastMessageTime: this.chatData.chat.lastMessageTime,
          silent:          newSilent
        }
      } else {
        body = {
          chatId:          this.chatData.chat.chatId,
          chatName:        this.chatData.chat.chatName,
          groupChat:       this.chatData.chat.groupChat,
          lastMessageTime: this.chatData.chat.lastMessageTime,
          silent:          newSilent
        }
      }
      const c = this.userService.setChatSettings(body)
      if ( c ) {
        c.subscribe({
          next: (response) => {
            if (response.ok) {
              if (this.chatData) {
                this.responseMessage = response.body.message
                const newChatData: ChatData = {
                  chat: body,
                  messages: this.chatData.messages,
                  partitionOffsets:  this.chatData.partitionOffsets,
                  users:  this.chatData.users
                  
                }
                this.userService.changeChat(newChatData)
              }
            } else {
              console.log('Changing chat settings has gone wrong')
            }
          },
          error: (err) => {
            console.log(err)
            this.responseMessage = err.error            
          },
          complete: () => {},
        })
      }   
    }
  }



  // if we do not want change data we can navigate back to chat side
  onCancel() {
    if (this.chatData){
      this.userService.updateSession()
      this.router.navigate(['user', 'chat', `${this.chatData.chat.chatId}`]);
    } else {
      this.router.navigate(['user']);
    }    
  }

  backToChat() {
    if (this.chatData){
      this.userService.updateSession()
      this.router.navigate(['user', 'chat', `${this.chatData.chat.chatId}`]);
    } else {
      this.router.navigate(['user']);
    }    
  }



  // here we handle request to leave chat. 
  leaveChat() {
    console.log('onDelete was called.')
  }

  clearNotification() {
    this.userService.updateSession()
    this.responseMessage = undefined
  }

}

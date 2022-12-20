import { APP_INITIALIZER, EventEmitter, Injectable } from '@angular/core';
import { ChatData } from '../models/ChatData';
import { Message } from '../models/Message';
import { User } from '../models/User';

@Injectable({
  providedIn: 'root'
})
export class ChatsDataService {

  chatAndUsers: Array<ChatData> = new Array();

  constructor() { }
  
  initialize(chats: ChatData[]) {
    this.chatAndUsers = chats.sort(
      (a,b) => -(a.chat.lastMessageTime - b.chat.lastMessageTime)
    )
    .map(
      (cd) => {
        cd.emitter = new EventEmitter<ChatData>()
        return cd
      }
    )
  }


  addNewChat(c: ChatData) {
    this.changeChat(c)
  }


  insertMessage(m: Message) {
    // here we add message to message in chat data. 
    const cd = this.chatAndUsers.find((cd, i , arr) => {
      return cd.chat.chatId == m.chatId
    })
    if ( cd ) {
      // trzeba jeszcze posortować wiadomości  
      // i zmienić last message tak aby poprawnie zostało
      // to posortowane
      cd.messages.push(m)


      this.changeChat( cd )
    }
  }
  
  


  deleteChat(c: ChatData) {
    this.chatAndUsers = this.chatAndUsers.filter((cd, i, arr) => {return cd.chat.chatId != c.chat.chatId})
      .sort((a,b) => -(a.chat.lastMessageTime - b.chat.lastMessageTime))
  }




  insertChatUsers(chatD: ChatData, u: User[]) {
    this.chatAndUsers = this.chatAndUsers.map((cd, i , arr) => {
      if (cd.chat.chatId == chatD.chat.chatId) {
        const newCD: ChatData = {
          chat: cd.chat,
          messages: cd.messages, 
          partitionOffsets: cd.partitionOffsets,
          users: u, // users are added
          emitter: cd.emitter
        }
        return newCD
      } else {
        return cd // otherwise return not changed
      }
    })
  }




  changeChat(chatD: ChatData) {
    const filtered = this.chatAndUsers.filter((cd, i, arr) => {return cd.chat.chatId != chatD.chat.chatId})
    filtered.push(chatD)
    this.chatAndUsers = filtered.sort((a,b) => -(a.chat.lastMessageTime - b.chat.lastMessageTime))
  }




  clear() {
    this.chatAndUsers = new Array()
  }
}

import { EventEmitter, Injectable } from '@angular/core';
import { ChatData } from '../models/ChatData';
import { Message } from '../models/Message';
import { MessagePartOff } from '../models/MesssagePartOff';
import { User } from '../models/User';

@Injectable({
  providedIn: 'root'
})
export class ChatsDataService {

  selectedChat: string | undefined // chatId

  chatAndUsers: Array<ChatData> = new Array();

  constructor() { }
  
  initialize(chats: ChatData[]) {
    this.chatAndUsers = chats.map(
      (cd) => {
        cd.users =  new Array<User>()
        cd.messages = new Array<Message>()
        cd.unreadMessages = new Array<MessagePartOff>()
        cd.emitter = new EventEmitter<ChatData>()
        return cd
      }
    ).sort(
      (a,b) => this.compareLatestChatData(a,b)
    )
  }


  addNewChat(c: ChatData) {
    this.changeChat(c)
  }



  // todo // zaimplementować,że w danym czacie wszystkie wiadomości są już przeczytane
  // po wywołaniu tej funkcji należy jeszcze fetchować ??? dane 
  markMessagesAsRead(chatId: string) {
    const chat = this.chatAndUsers.find(
      (cd, i , arr) => {
        return cd.chat.chatId == chatId
      }
    )
    if ( chat ) {
      chat.isNew = false
      if ( chat.unreadMessages.length > 0 ) {
        chat.unreadMessages.forEach(
          (m, i, arr) => {
            chat.messages.push( m.msg )
            if (m.msg.serverTime > chat.chat.lastMessageTime) chat.chat.lastMessageTime = m.msg.serverTime
            chat.partitionOffsets = chat.partitionOffsets.map(
              (po, i, arr) => {
                if (po.partition == m.p && po.offset < m.o){ 
                  po.offset = m.o
                  return po
                } else  {
                  return po
                }
              }
            )
          }
        )
        chat.messages.sort((a,b) => a.serverTime - b.serverTime )
        this.changeChat( chat )
      } 
      // w każdym miejscu w którym opuszczamy chat należy wywołać 
      // this.userService.selectChat( undefined )
    }
  }


  insertMessage(m: MessagePartOff) {
    const found = this.chatAndUsers.find(
      (cd, i , arr) => {
        return cd.chat.chatId == m.msg.chatId
      }
    )
    if ( found ) {
      if (m.msg.chatId == this.selectedChat) {
        found.messages.push(m.msg)
        found.partitionOffsets = found.partitionOffsets.map(
          (po, i, arr) => {
            if (po.partition == m.p && po.offset < m.o){ 
              po.offset = m.o
              return po
            } else  {
              return po
            }
          }
        )
        found.chat.lastMessageTime = m.msg.serverTime
        found.messages.sort((a,b) => a.serverTime - b.serverTime )
        this.changeChat( found )
      } else {
        found.unreadMessages.push( m )
        found.chat.lastMessageTime = m.msg.serverTime
        this.changeChat( found )
      }
    }  
  }
    
    
    /*
          i w subskrybencie zrobić tak, że jeśli offset jest mniejszy 
          niż ten zapisany w  chatdata to należy zapisać wiadomość do readMessages
          natomiast jeśli nie większy to należy zpaisac zarówno wiadomość jak i offset 
          do unreadmessages.

          Następnie jak będziemy wchodzić w chat panel to przy ładowaniu componentu
          sprawdzamy czy ma jakieś unread, jeśli ma to aktualizujemy partitionoffsets 
          i dodajemy wiadomości do przeczytanych. 

          W przypadku gdy jest to czat otwarty w którym jesteśmy to 
          powinniśmy w subscrybencie ??? sprawdzić ścieżkę w której jesteśmy
          i jeśli jest ta sama jak chatId w wiadomości to powinniśmy od razu dodać do read messages
          i updejtować partition offsets. 


    */
    


    /*
    pomysł na rozwiązanie:

    wprowadzić zmienną selectedChatId i jeśli jest ona zdefiniowana to znaczy, że dana strona jest aktywna
    i każdą wiadomość w tego czatu nalezy dodać do przeczytanych. 


    insertujemy tę wiadomość jako nieprzeczytaną następnie wiemy, że po tej wiadomości 
    emitowany jest fetch data emiter, 
    subskrybując ten emmiter powinniśmy sprawdzić jaki jest chatId w ścierzce (to w niektych sybskrybentach jest już zrobione)
    i mając ten chatId wywołać w userService markAllMessagesAsRead(chatId) następnie to przekazuje dalej do chatService
    w chatService przenosimy te wiadomości do readMessages, obliczamy nowy lastMessage time 
    i ponownie sortujemy ??? -> ale to powoduje, że dwa razy wykonujemy sortowanie całej listy. 
    
    ŻLE to jest


    */
    
    
/*     const cd = this.chatAndUsers.find((cd, i , arr) => {
      return cd.chat.chatId == m.msg.chatId
    })
    if ( cd ) {
      const mess = cd.messages
      mess.push(m)
      cd.messages = mess.sort((a,b) => { return a.utcTime - b.utcTime })
      cd.chat.lastMessageTime = m.msg.serverTime
      this.changeChat( cd )
    }
  }
  
 */  


  deleteChat(c: ChatData) {
    this.chatAndUsers = this.chatAndUsers.filter((cd, i, arr) => {return cd.chat.chatId != c.chat.chatId})
      .sort((a,b) => this.compareLatestChatData(a,b) )
  }




  insertChatUsers(chatId: string, u: User[]) {
    this.chatAndUsers = this.chatAndUsers.map( (cd, i , arr) => {
      if (cd.chat.chatId == chatId) {
        const newCD: ChatData = {
          chat: cd.chat,
          messages: cd.messages, 
          partitionOffsets: cd.partitionOffsets,
          users: u, // users are added
          unreadMessages: cd.unreadMessages,
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
    this.chatAndUsers = filtered.sort((a,b) => this.compareLatestChatData(a,b) )
  }


  selectChat(chatId: string | undefined ) {
    this.selectedChat = chatId
  }


  compareLatestChatData(c1: ChatData, c2: ChatData): number {
    let data1 = 0
    let data2 = 0
    if (c1.unreadMessages.length == 0)
      data1 = c1.chat.lastMessageTime
    else {
      c1.unreadMessages.forEach(
        (m,i,a) => {
          if (m.msg.serverTime > data1) data1 = m.msg.serverTime
        }
      )
    }
    if (c2.unreadMessages.length == 0)
      data2 = c2.chat.lastMessageTime
    else {
      c2.unreadMessages.forEach(
        (m,i,a) => {
          if (m.msg.serverTime > data1) data2 = m.msg.serverTime
        }
      )
    }
    return -data1 + data2
  }



  clear() {
    this.chatAndUsers = new Array()
  }
}

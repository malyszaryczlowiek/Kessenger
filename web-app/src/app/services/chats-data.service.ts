import { EventEmitter, Injectable } from '@angular/core';
import { ChatData } from '../models/ChatData';
import { Message } from '../models/Message';
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
        cd.unreadMessages = new Array<Message>()
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
      this.selectedChat = chatId 
      if ( chat.unreadMessages.length > 0 ) {
        chat.unreadMessages.forEach(
          (m, i, arr) => {
            chat.messages.push( m )
            if (m.serverTime > chat.chat.lastMessageTime) chat.chat.lastMessageTime = m.serverTime
            chat.partitionOffsets = chat.partitionOffsets.map(
              (po, i, arr) => {
                if (po.partition == m.partOff.partition && po.offset < m.partOff.offset){ 
                  po.offset = m.partOff.offset
                  return po
                } else  {
                  return po
                }
              }
            )
          }
        )
        chat.unreadMessages = new Array<Message>()
        chat.messages = chat.messages.sort((a,b) => a.serverTime - b.serverTime )
        this.changeChat( chat )
      } 
    } else {
      // this.selectedChat = undefined
    }
  }



  insertNewMessages(m: Message[]) {
    m.forEach((mm,i,arr) => {
      const foundCD = this.chatAndUsers.find((cd, i, arr) => {
        return cd.chat.chatId == mm.chatId
      })
      if ( foundCD ) {
        if ( foundCD.chat.chatId == this.selectedChat) {
          foundCD.messages.push( mm )
          foundCD.messages = foundCD.messages.sort((a,b) => a.serverTime - b.serverTime )
          foundCD.partitionOffsets = foundCD.partitionOffsets.map(
            (po, i, arr) => {
              if (po.partition == mm.partOff.partition && po.offset < mm.partOff.offset){ 
                po.offset = mm.partOff.offset
                return po
              } else  {
                return po
              }
            }
          )
        } else {
          foundCD.unreadMessages.push( mm )
        }
        if (foundCD.chat.lastMessageTime < mm.serverTime) foundCD.chat.lastMessageTime = mm.serverTime
        this.changeChat( foundCD )
      }
    })
  }

    // gdzieś trzeba jeszcze wysłać powiadomienia przez websocket,
    // że w danym chatcie po odczytaniu wiadomości mamy nowy offset 
    // od którego będzie przy następnym pobiernaiu wiadomości zacząć. 





  insertOldMessages(m: Message[]) {
    const chatId = m.at(0)?.chatId
    if ( chatId ) {
      const found = this.chatAndUsers.find(
        (cd, i , arr) => {
          return cd.chat.chatId == chatId
        }
      )
      if ( found ) {
        m.forEach((mess, i, arr) => found.messages.push(mess))
        found.messages = found.messages.sort((a,b) => a.serverTime - b.serverTime )
        this.changeChat( found )
        found.emitter.emit( found )
      }
    }
  }
  



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


  private compareLatestChatData(c1: ChatData, c2: ChatData): number {
    let data1 = 0
    let data2 = 0
    if (c1.unreadMessages.length == 0)
      data1 = c1.chat.lastMessageTime
    else {
      c1.unreadMessages.forEach(
        (m,i,a) => {
          if (m.serverTime > data1) data1 = m.serverTime
        }
      )
    }
    if (c2.unreadMessages.length == 0)
      data2 = c2.chat.lastMessageTime
    else {
      c2.unreadMessages.forEach(
        (m,i,a) => {
          if (m.serverTime > data2) data2 = m.serverTime
        }
      )
    }
    return -data1 + data2
  }



  clear() {
    this.chatAndUsers = new Array()
  }
}

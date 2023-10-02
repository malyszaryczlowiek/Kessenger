import { EventEmitter, Injectable } from '@angular/core';
import { ChatData } from '../models/ChatData';
import { Message } from '../models/Message';
import { User } from '../models/User';

@Injectable({
  providedIn: 'root'
})
export class ChatsDataService {

  selectedChat: string | undefined // chatId
  myUserId: string = ''
  
  isRead = false 

  chatAndUsers: Array<ChatData> = new Array();

  constructor() { }
  
  initialize(chats: ChatData[], userId: string) {
    this.myUserId = userId
    this.chatAndUsers = chats.map(
      (cd) => {
        cd.users =  new Array<User>()
        cd.messages = new Array<Message>()
        cd.unreadMessages = new Array<Message>()
        cd.emitter = new EventEmitter<ChatData>()
        return cd
      }
    ).sort( (a,b) => this.compareLatestChatData(a,b) )
  }


  addNewChat(c: ChatData) {
    this.changeChat(c)
  }



  // todo // zaimplementować,że w danym czacie wszystkie wiadomości są już przeczytane
  // po wywołaniu tej funkcji należy jeszcze fetchować ??? dane 
  markMessagesAsRead(chatId: string): {cd: ChatData, num: number} | undefined {
    const chat = this.chatAndUsers.find(
      (cd, i , arr) => {
        return cd.chat.chatId == chatId
      }
    )
    if ( chat ) {
      chat.isNew = false
      this.selectedChat = chatId 
      const num = chat.unreadMessages.length
      if ( num > 0 ) {
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
        // this.changeChat( chat )
      } 
      this.changeChat( chat )
      return { cd: chat, num: num }
    } else {
      return undefined
    }
  }

  //tutaj // jest problem z wczytaniem wielu wiadomości 
  // oraz z tym że jak jesteśmy w liście czatów i zrobimy refresh
  // to wszystkie wiadomości są wczytywane do nowych wiadomości 
  // dlatego trzeba jeszcze zimplementować mechanizm sprawdzający
  // czy dana wiadomość ma offset poniżej czy powyżej offsetu w danym chacie.

  insertNewMessages(m: Message[]) {
    // let code = 2

    error
    // tutaj należy dodać  wysyłanie przez ws update chat offset



    m.forEach((mm,i,arr) => {
      const foundCD = this.chatAndUsers.find((cd, i, arr) => {
        return cd.chat.chatId == mm.chatId
      })
      if ( foundCD ) {

//         tutaj jest problem ######################################################################################################################################################
        // nalezy zrobić tak aby sprawdzać czy jak przychodzi wiadomość w nowo utworzonym chacie 
        // to należy dodać te wiadomości do unread a nie do przeczytanych 

        if ( foundCD.isNew ) {
          foundCD.unreadMessages.push( mm ) 
        } else {
          const unread = foundCD.partitionOffsets.some((po,i,arr) => {
            return po.partition == mm.partOff.partition && po.offset <= mm.partOff.offset // ##### tuaj zmieniłem
          })
          if ( unread  ) foundCD.unreadMessages.push( mm )  // && mm.authorId != this.myUserId
          else {
            console.log('WIADOMOŚĆ DODANA DO PRZECZYTANYCH')
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
          }  
        }
        if (foundCD.chat.lastMessageTime < mm.serverTime) foundCD.chat.lastMessageTime = mm.serverTime
        this.changeChat( foundCD )
      }
    })
  }


   
  // deprecated
  /*
insertNewMessages2(m: Message[]): number {
    let code = 2
    m.forEach((mm,i,arr) => {
      const foundCD = this.chatAndUsers.find((cd, i, arr) => {
        return cd.chat.chatId == mm.chatId
      })
      if ( foundCD ) {
        if ( foundCD.chat.chatId == this.selectedChat) {
          if (code == 2) code = 1
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
          //sprawdzić // czy offset wiadomości jest mniejszy niż offset w chatcie
          // jeśli tak to trafia do przeczytanych, 
          // jeśli jest większy to do nieprzeczytanych. 
          const unread = foundCD.partitionOffsets.some((po,i,arr) => {
            return po.partition == mm.partOff.partition && po.offset < mm.partOff.offset
          })
          if ( unread ) foundCD.unreadMessages.push( mm ) 
          else {
            console.log('WIADOMOŚĆ DODANA DO PRZECZYTANYCH')
            if (code == 2) code = 1
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
          }          
        }
        if (foundCD.chat.lastMessageTime < mm.serverTime) foundCD.chat.lastMessageTime = mm.serverTime
        this.changeChat( foundCD )
      }
    })
    return code
  }

  */


  
    // gdzieś trzeba jeszcze wysłać powiadomienia przez websocket,
    // że w danym chatcie po odczytaniu wiadomości mamy nowy offset 
    // od którego będzie przy następnym pobiernaiu wiadomości zacząć. 


/*   canFetchOlderMessages(chatId: string): boolean {
    const cd = this.chatAndUsers.find((cd,i,arr) => {
      return cd.chat.chatId == chatId
    })
    if (cd) {
      let r = false
      cd.messages.find((m,i,ar) => {
        return m.partOff.
      })


      return true
    }
    else return false
  }
 */


  insertOldMessages(m: Message[]) {
    const chatId = m.at(0)?.chatId
    if ( chatId ) {
      const found = this.chatAndUsers.find( (cd, i , arr) => { return cd.chat.chatId == chatId } )
      if ( found ) {
        m.forEach((mess, i, arr) => {
          found.messages.push(mess)
        })
        found.messages = found.messages.sort((a,b) => a.serverTime - b.serverTime )
        this.changeChat( found )
        console.warn('ChatsDataService.insertOldMessage() inserting old messages')
        found.emitter.emit( found )
      }
    } else {
      console.warn('ChatsDataService.insertOldMessages() => chatId NOT KNOWN. ')
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

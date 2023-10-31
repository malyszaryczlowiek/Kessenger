import { EventEmitter, Injectable } from '@angular/core';
// services
import { HtmlService } from 'src/app/services/html.service';
// models
import { Writing } from '../models/Writing';
import { ChatOffsetUpdate } from '../models/ChatOffsetUpdate';
import { ChatData } from '../models/ChatData';
import { Message } from '../models/Message';
import { User } from '../models/User';



@Injectable({
  providedIn: 'root'
})
export class ChatsDataService {

  selectedChat: string | undefined // chatId
  user:           User | undefined

  chatAndUsers: Array<ChatData> = new Array();


  // to musi być zasubskrybowane przez connection service
  // tam będzie wywoływana metoda do wysyłania przez ws informacji o updejcie
  chatOffsetUpdateEmitter: EventEmitter<ChatOffsetUpdate> = new EventEmitter<ChatOffsetUpdate>()
  

  // te emitery muszą być zasubskrybowane przez odpowiednie komponenty 
  // component subskrybując updejtuje - pobiera dane z serwisu
  updateChatListEmmiter:   EventEmitter<number> = new EventEmitter<number>()     // tuaj może pozostać any 
  updateChatPanelEmmiter:  EventEmitter<number> = new EventEmitter<number>()     // tutaj też może być any, bo jeśli tylko dostajemy event to wiadomo, 
    

  // ten emmiter musi być zasubskrybowany do obierania informacji z connection, że ktoś piszę 
  // subskrybują go chat-panel i chat-list
  receivingWritingEmitter: EventEmitter<Writing | undefined> = new EventEmitter<Writing| undefined>()


    // tym emiterem informujemy connection service, że chcemy pobrać przez WS stare wiadomości z backendu
  // że jest to z tego czatu w którym jesteśmy i że jesteśmy na samym dole
  fetchOlderMessagesEmitter: EventEmitter<string> = new EventEmitter<string>()


  constructor(private htmlService: HtmlService) {}
  


  initialize(chats: ChatData[], u: User) {
    this.user = u
    this.chatAndUsers = chats.map(
      (cd) => {
        cd.users          = new Array<User>()
        cd.messages       = new Array<Message>()
        cd.unreadMessages = new Array<Message>()
        cd.emitter        = new EventEmitter<ChatData>()
        return cd
      }
    ).sort( (a,b) => this.compareLatestChatData(a,b) )
  }





  findChat(chatId: string): ChatData | undefined {
    return this.chatAndUsers.find((cd, i, arr) => {
      return cd.chat.chatId == chatId
    })
  }



  /*
    method called when we created new chat and we get response that all chat data, and all stuff 
    are handled correctly by backend

    method is called when we get invitation to the chat and we successfully receive alle chat data from
    backend server.
  */
  addNewChat(c: ChatData) {
    this.changeChat(c)
    // we need notify that ne chat was added
    this.updateChatListEmmiter.emit( 0 )
  }



  /*
    method called when we do not have any chat, 
    but we downloaded chat data separately.
    (for example after creation of our first chat)
  */
  setChats(chats: ChatData[]) {
    chats.forEach(
      (c, i, arr) => {
        this.changeChat( c )
      }
    )
  }



  /*
    method called when we get info from connectionService that someone whas writing in any
    of our chat. emitter emits this event and subscribers 
    from chat-list or chat-panel may display this information if necessary. 
  */
  showWriting(w: Writing | undefined) {
    this.receivingWritingEmitter.emit( w )
  }



  setChatId(cId: string ) {
    this.selectedChat = cId;
  }



  markMessagesAsRead(chatId: string) {
    const chat = this.findChat( chatId )
    if ( chat ) {
      console.log('markMessages as read. ')
      chat.isNew = false
      this.selectedChat = chatId 
      const num = chat.unreadMessages.length
      // if no unread messages, we do nothing and simply end method
      if ( num > 0 ) {
        chat.unreadMessages.forEach(
          (m, i, arr) => {
            // move each unread message to read 
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
        let cd = { cd: chat, num: num }
        if ( this.user && cd ) {
          if ( cd.num > 0 ) {
            const chatOffsetUpdate: ChatOffsetUpdate = {
              userId:           this.user.userId,
              chatId:           cd.cd.chat.chatId,
              lastMessageTime:  cd.cd.chat.lastMessageTime,
              partitionOffsets: cd.cd.partitionOffsets 
            }    
            this.chatOffsetUpdateEmitter.emit( chatOffsetUpdate )

          }
        }
        this.updateChatListEmmiter.emit( 0 )
        this.updateChatPanelEmmiter.emit( 0 )
      }  
    }  
  }



  insertNewMessages2(m: Message[]) {
    console.log('ChatsDataService.insertNewMessages2() ')
    m.forEach((mm, i, arr) => {
      if (mm.chatId == this.selectedChat) {
        // we are on the bottom of chat so add to read messages
        if ( this.htmlService.isScrolledDown() == 1 )  {
          const c = this.findChat( mm.chatId )
          if ( c ) {
            c.messages.push( mm )
            c.messages = c.messages.sort((a,b) => a.serverTime - b.serverTime )
            c.partitionOffsets = c.partitionOffsets.map(
              (po, i, arr) => {
                if (po.partition == mm.partOff.partition && po.offset < mm.partOff.offset){ 
                  po.offset = mm.partOff.offset
                  return po
                } else  {
                  return po
                }
              }
            )
            if (this.user) {
              const chatOffsetUpdate: ChatOffsetUpdate = {
                userId:           this.user.userId, 
                chatId:           c.chat.chatId,
                lastMessageTime:  c.chat.lastMessageTime,
                partitionOffsets: c.partitionOffsets 
              } 
              console.log('ChatsDataService.insertNewMessages2() -> emitting chatOffsetUpdateEmitter event')
              this.chatOffsetUpdateEmitter.emit( chatOffsetUpdate )
            }
            this.changeChat( c )
            this.htmlService.scrollDown( true )
          }          
        } 
        // if we are not at the bottom of chat, so we do not see new message(s) and need add them to unreadMessages
        else  {
          const c = this.findChat( mm.chatId )
          if ( c ) {
            console.log('ChatsDataService.insertNewMessages2() -> adding NEW messages to UNREAD messages, because of we are NOT scrolled down')
            c.unreadMessages.push( mm ) 
            this.changeChat( c )
          }
        }
      } else { // other chat than actually selected
        const c = this.findChat( mm.chatId )
        if ( c ) {
          console.log('ChatsDataService.insertNewMessages2() -> adding NEW messages to UNREAD messages, because of we are in other chat than selected')
          c.unreadMessages.push( mm ) 
          this.changeChat( c )
        }
      }
    })
    this.updateChatListEmmiter.emit( 0 )
    this.updateChatPanelEmmiter.emit( 0 )
  } 



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
        console.warn('ChatsDataService.insertOldMessage() -> inserting old messages')
        this.updateChatPanelEmmiter.emit( 0 )
      }
    } else {
      console.warn('ChatsDataService.insertOldMessages() -> chatId NOT KNOWN. ')
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
    this.updateChatListEmmiter.emit( 0 )
    this.updateChatPanelEmmiter.emit( 0 )
  }


  updateChatPanel() {
    console.log('ChatsDataService.updateChatPanel() -> emitting updateChatPanelEmmiter event ')
    this.updateChatPanelEmmiter.emit( 0 )
  }



  updateChatList() {
    console.log('ChatsDataService.updateChatList() -> emitting updateChatListEmmiter event ')
    this.updateChatListEmmiter.emit( 0 )
  }


  changeChat(chatD: ChatData) {
    const filtered = this.chatAndUsers.filter((cd, i, arr) => {return cd.chat.chatId != chatD.chat.chatId})
    filtered.push(chatD)
    this.chatAndUsers = filtered.sort((a,b) => this.compareLatestChatData(a,b) )
  }





  selectChat(chatId: string | undefined ) {
    if (chatId){ 
      const found = this.findChat( chatId )
      if ( found ){
        this.selectedChat = chatId
        console.log(`ChatDataService.selectChat() -> selected chat found in chat list `)
        this.fetchOlderMessages( this.selectedChat )
        this.markMessagesAsRead( this.selectedChat )
      } else {
        console.warn(`ChatDataService.selectChat() -> selected chat NOT found in chat list `)
        this.selectedChat = undefined
      }
    }
  }





  clearSelectedChat() {
    console.log(`ChatDataService.clearSelectedChat()`)
    this.selectedChat = undefined
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
    this.user         = undefined
    this.selectedChat = undefined
  }

  




  fetchOlderMessages(chatId: string) {
    console.log(`ChatDataService.fetchOlderMessages() -> emitting signal to ConnectionService fetch older messages`)
    this.fetchOlderMessagesEmitter.emit( chatId )
  }



  /*
    method called in chat-panel to fetching chat data
  */
  getCurrentChatData(): ChatData | undefined {
    if (this.selectedChat) {
      return this.findChat( this.selectedChat )
    } else return undefined
  }


  setUser(u: User) {
    this.user = u
  }






}
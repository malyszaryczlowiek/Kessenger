import { EventEmitter, Injectable } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
// services
import { HtmlService } from 'src/app/services/html.service';
// models
import { Chat } from '../models/Chat';
import { Writing } from '../models/Writing';
import { ChatOffsetUpdate } from '../models/ChatOffsetUpdate';
import { ChatData } from '../models/ChatData';
// import { Invitation } from '../models/Invitation';
import { Message } from '../models/Message';
import { PartitionOffset } from '../models/PartitionOffset';
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
  

  // informacja o tym, że ktoś pisze jest wysyłana do connection, które ma subskrybenta, 
  // który to przechwytuje i wysyła przez WS
  // sendingWritingEmitter:   EventEmitter<Writing | undefined> = new EventEmitter<Writing| undefined>()
  

  // ten emmiter musi być zasubskrybowany do obierania informacji z connection, że ktoś piszę 
  // subskrybują go chat-panel i chat-list
  receivingWritingEmitter: EventEmitter<Writing | undefined> = new EventEmitter<Writing| undefined>()


  // tym emitterm wysyłamy wiadomość do connection service żeby wysłać ją przez WS
  // sendMessageEmitter:      EventEmitter<Message> = new EventEmitter<Message>()
  

  // tym emiterem informujemy connection service, że chcemy pobrać przez WS stare wiadomości z backendu
  // że jest to z tego czatu w którym jesteśmy i że jesteśmy na samym dole
  fetchOlderMessagesEmitter: EventEmitter<string> = new EventEmitter<string>()



  // tym emitterem informujemy, ze dane zostały już zainicjalizowane. - narazie jednak z tego rezygnuje. 
  // initializationFinishedEmitter: EventEmitter<number> = new EventEmitter<number>()
  





  // rezygnuję z zubskrypcji na rzecz bezpośredniego wywoływania metody tego serwisu
  // newMessagesSubscription:      Subscription | undefined
  // oldMessagesSubscription:      Subscription | undefined
  // invitationSubscription:       Subscription | undefined



  constructor(private htmlService: HtmlService, private activated: ActivatedRoute) {}
  


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

    // here we check our path and if 
    const chatId = this.activated.snapshot.paramMap.get('chatId');
    if ( chatId ) { 
      console.error(`ERRRRRORROOOOOOOORRRRRRRR`)
      this.selectChat( chatId )
    }


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
    method called from chat panel when we sending info that we are typing
  */
  /* sendWriting(w: Writing) {
    this.sendingWritingEmitter.emit( w )
  } */


  /*
    method called when we get info from connectionService that someone whas writing in any
    of our chat. emitter emits this event and subscribers 
    from chat-list or chat-panel may display this information if necessary. 
  */
  showWriting(w: Writing | undefined) {
    this.receivingWritingEmitter.emit( w )
  }



  /*
    method called from connection service when we get invitation via WS
  */
/*   handleInvitation(i: Invitation) {
    if ( i ) {
      const cd: ChatData =  {
        chat: i.chat,
        partitionOffsets: invitation.partitionOffsets,
        messages: new Array<Message>(),
        unreadMessages: new Array<Message>(),
        users: new Array<User>(),
        isNew: true,
        emitter: new EventEmitter<ChatData>()  
      }
      this.addNewChat( cd ) 
      this.startListeningFromNewChat( cd.chat.chatId, cd.partitionOffsets )
      
      this.dataFetched( 2 ) 

      if ( this.user ) {
        const bodyToSent: UserOffsetUpdate = {
          userId: this.user.userId,
          joiningOffset: invitation.myJoiningOffset                    
        }
        const u = this.updateJoiningOffset( bodyToSent )
        if ( u ) {
          const sub = u.subscribe({
            next: (response) => {
              if ( response.ok ) {
                this.settingsService.settings.joiningOffset = invitation.myJoiningOffset
                console.log('joining Offset updated ok. to ', invitation.myJoiningOffset)
              }
                
            },
            error: (err) => {
              console.log('Error during joining offset update', err)
            },
            complete: () => {}
          })
        }
      }                  
    } 
  }
 */

  // todo // zaimplementować,że w danym czacie wszystkie wiadomości są już przeczytane
  // po wywołaniu tej funkcji należy jeszcze fetchować ??? dane 
  markMessagesAsRead(chatId: string) { // : {cd: ChatData, num: number} | undefined
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

        // poniższe dodałem

        if ( this.user && cd ) {
          if ( cd.num > 0 ) {
            const chatOffsetUpdate: ChatOffsetUpdate = {
              userId:           this.user.userId,
              chatId:           cd.cd.chat.chatId,
              lastMessageTime:  cd.cd.chat.lastMessageTime,
              partitionOffsets: cd.cd.partitionOffsets 
            }    
            this.chatOffsetUpdateEmitter.emit( chatOffsetUpdate )
            //this.connection.sendChatOffsetUpdate( chatOffsetUpdate )
          }
        }
        this.updateChatListEmmiter.emit( 0 )
        this.updateChatPanelEmmiter.emit( 0 )
      }  // end of if num
    }  // end of if (chat)

    //    else {
//      return undefined
// }      
  }






  //tutaj // jest problem z wczytaniem wielu wiadomości 
  // oraz z tym że jak jesteśmy w liście czatów i zrobimy refresh
  // to wszystkie wiadomości są wczytywane do nowych wiadomości 
  // dlatego trzeba jeszcze zimplementować mechanizm sprawdzający
  // czy dana wiadomość ma offset poniżej czy powyżej offsetu w danym chacie.

  /*
  method returns code informing if we should update chat list, chat panel, both, 
  or do not update nothing
  */
  //  DEPRECATED
  insertNewMessages1(m: Message[]): number {
    let code = 0
    // tutaj należy dodać  wysyłanie przez ws update chat offset
    // error
    m.forEach((mm,i,arr) => {
      const foundCD = this.chatAndUsers.find((cd, i, arr) => {
        return cd.chat.chatId == mm.chatId
      })
      if ( foundCD ) {

//         tutaj jest problem ######################################################################################################################################################
        // nalezy zrobić tak aby sprawdzać czy jak przychodzi wiadomość w nowo utworzonym chacie 
        // to należy dodać te wiadomości do unread a nie do przeczytanych 

        if (foundCD.chat.lastMessageTime < mm.serverTime) foundCD.chat.lastMessageTime = mm.serverTime
        if ( foundCD.isNew ) {
          foundCD.unreadMessages.push( mm ) 
          if ( code == 0 ) code = 2
          if ( code == 3 ) code = 1
        } else {
          const unread = foundCD.partitionOffsets.some((po,i,arr) => {
            return po.partition == mm.partOff.partition && po.offset < mm.partOff.offset // ##### tutaj zmieniłem
          })
          if ( unread  ) {
            foundCD.unreadMessages.push( mm )  // && mm.authorId != this.myUserId
            if ( code == 0 ) code = 2
            if ( code == 3 ) code = 1
          }
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
            if (this.user) {
              const chatOffsetUpdate: ChatOffsetUpdate = {
                userId:           this.user.userId, // 'undefined'
                chatId:           foundCD.chat.chatId,
                lastMessageTime:  foundCD.chat.lastMessageTime,
                partitionOffsets: foundCD.partitionOffsets 
              } 
              this.chatOffsetUpdateEmitter.emit( chatOffsetUpdate )
            }            
            // if ( code != 1 ) code = 1
          }  
        }
        this.changeChat( foundCD )
      }
    })

    return code;
  }






    /*
  rozwiązanie by na każdy event był inny emiter tzn
  1. jak przychodzi nowa wiadomość to chat-service to przetwarza a następnie wysyła 
     event do wszystkich subskrybentów i tak np aktualizuje się lista chatów tak aby była poprawnie posortowana    
     w przypadku chat-panel event będzie wysłany tylko wtedy gdy aktualny chat w chat poanel jest zgodny z tym który jest tutaj w servisie
     i chat-panel jest ustawione na samym dole. 
  2.    
  */







  // sprawdzić czy algorytm jest poprawny i zacząć implemenmtować
  /*
  ta metoda będzie zawierała dwa nowe emmitery: chatPanelEmmiter i chatListEmmiter


  1. jak przychodzi nowa wiadomość to sprawdzamy czy wiadomość pochodzi z chatu, który jest aktualnie selected
    -- jest SELECTED
       2. sprawdzamy czy jesteśmy na samym dole w htmlService
         -- TAK jesteśmy na dole
            3. dodajemy do przeczytanych
            4. akualizujemy listę czatów (tutaj chodzi o to by nie było info o nieprzeczytanych wiadomościach i by czaty były w kolejności zgodnej z najświeższymi wiadomościami od góry)
            5. wysyłamy informację o update chat offset
            6. aktualizujemy chatPanel stosując emmiter w chatData (tutaj chodzi o to by lista wiadomości w czacie już zawierała nowo dodane wiadomości)
            <koniec>
         -- NIE 
            3. dodajemy do NIEPRZECZYTANYCH
            4. akualizujemy listę czatów  (tutaj chodzi o to by BYŁA informacja o nieprzeczytanych wiadomościach i by czaty były w kolejności zgodnej z najświeższymi wiadomościami od góry)

            w chat-panel mamy subscrybenta, który sprawdza czy zjechaliśmy na sam dół czatu
            -- TAK, zjechaliśmy to 
              1. wymuszamy dodanie wszystkich nieprzeczytanych wiadomości w chacie do przeczytanych
              2. aktualizujemy chat-listę, bo nie powinna wyświetlać, że mamy nieprzeczytane wiadomości
    -- NIE jest selected
      2. dodajemy do nieprzeczytanych
      3. aktualizujemy listę czatów po lewej stronie, tak aby wyświetlała, że mamy nieprzeczytane wiadomości 

  */
      // problem // z tym że po zalogowaniu przychodzi nowa wiadomość a nie jest na liście czatów wyświetlone, że mamy nową wiadomość. 

  insertNewMessages2(m: Message[]) {
    console.log('ChatsDataService.insertNewMessages2() ')
    m.forEach((mm, i, arr) => {
      if (mm.chatId == this.selectedChat) {
        // jeśli jesteśmy na dole to dodajemy do przeczytanych
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
                userId:           this.user.userId, // 'undefined'
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
        // jeśli nie jesteśmy na dole to dodajemy do nieprzeczytanych
        else  {
          const c = this.findChat( mm.chatId )
          if ( c ) {
            console.log('ChatsDataService.insertNewMessages2() -> adding NEW messages to UNREAD messages, because of we are NOT scrolled down')
            c.unreadMessages.push( mm ) 
            this.changeChat( c )
          }
        }
      } else { // inny czat niż aktualnie wybrany
        const c = this.findChat( mm.chatId )
        if ( c ) {
          console.log('ChatsDataService.insertNewMessages2() -> adding NEW messages to UNREAD messages, because of we are in other chat than selected')
          c.unreadMessages.push( mm ) 
          this.changeChat( c )
        }
      }
    })
    // updejtujemy wszystko co trzeba
    this.updateChatListEmmiter.emit( 0 )
    this.updateChatPanelEmmiter.emit( 0 )
  } 



   

  /*
    sprawdzić jeszcze czy mechanizm informowania (wysyłąnia do wszystkich komponnentów) 
    // o przyjściu starej wiadomości jest poprawny
    chyba, że trzeba tylko poinformować chat-panel 
    bo chat-list nie korzysta z informacji o starych wiadomościach. 
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
        // found.emitter.emit( found ) // w starej wersji
        this.updateChatPanelEmmiter.emit( 0 )
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




  // tutaj  oprócz selekcji powinniśmy jeszcze dla tego czatu:
  // 1. fetchować stare wiadomości ( po wykonaniu fetchowania należy jeszcze updejtować sesje - wewnątrz metody )
  // 2. oznaczyć wszystkie wiadomości jako przeczytane ( co powinno wywołać wysłanie emitu chatOffsetUpdateEmitter )

  //   zbadać jaki będzie mechanizm wczytywania danych do chat-panel jeśli:
  // 1) klikniemy w listę obok
  // 2) zrobimy reaload strony i wczytany chatId będzie  <<ze ścieżki>>

  selectChat(chatId: string | undefined ) {
    if (chatId){ //    &&  this.selectedChat !== chatId
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

/*     if (this.newMessagesSubscription) {
      this.newMessagesSubscription.unsubscribe()
      this.newMessagesSubscription = undefined
    }

    if (this.oldMessagesSubscription) {
      this.oldMessagesSubscription.unsubscribe()
      this.oldMessagesSubscription = undefined
    }

    if (this.invitationSubscription)  {
      this.invitationSubscription.unsubscribe()
      this.invitationSubscription = undefined
    }
 */
    this.chatAndUsers = new Array()
    this.user         = undefined
    this.selectedChat = undefined
  }

  


/*   sendMessage(msg: Message) {
    this.sendMessageEmitter.emit( msg )
  }
 */


  fetchOlderMessages(chatId: string) {
    this.fetchOlderMessagesEmitter.emit( chatId )
    // this.connection.fetchOlderMessages( chatId )
  }



/*   getWritingEmmiter() {
    return this.connection.writingEmitter
  }
 */



  /*
    method called in chat-panel to fetching chat data
  */
  getCurrentChatData(): ChatData | undefined {
    if (this.selectedChat) {
      return this.findChat( this.selectedChat )
      /* return this.chatAndUsers.find( (chatData, index, arr) => {
        return chatData.chat.chatId == this.selectedChat;
      }) */
    } else return undefined
  }


  setUser(u: User) {
    this.user = u
  }






/*   getChatData(chatId: string): Observable<HttpResponse<{chat: Chat, partitionOffsets: Array<{partition: number, offset: number}>}>> | undefined  {
    if (this.user) {
      this.updateSession(false)
      return this.connection.getChatData(this.user.userId, chatId);
    }
    else return undefined;
  }
 */

  /* startListeningFromNewChat(chatId: string, partitionOffsets: PartitionOffset[]) {
    this.connection.startListeningFromNewChat( chatId , partitionOffsets)
  } */


  // to wszystko trzebaby przenieść do connection service

  /* updateSession(sendUpdateToServer: boolean) {
    if (this.user) {
      this.connection.updateSession(sendUpdateToServer)
      //this.connection.updateSession(this.user.userId);
      this.restartLogoutTimer()
    }
  } */


















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






}

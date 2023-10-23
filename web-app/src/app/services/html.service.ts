import { EventEmitter, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class HtmlService {

  scrollInterval:    NodeJS.Timeout | undefined
  resizeInterval:    NodeJS.Timeout | undefined
  

  maxScrollPosition: number = 1

  messageListScrollEventEmitter = new EventEmitter<string>() 
  

  constructor() {
    console.log('starting html service.')
    window.addEventListener("resize" , (event) => {
      this.resizeMessageListImmediately()
      this.resizeChatListImmediately()
      this.resizeSelectChatImmediately()
    })
  }



  closeService() {
    if ( this.scrollInterval ) clearTimeout( this.scrollInterval )
    if ( this.resizeInterval ) clearTimeout( this.resizeInterval )    
  }





  /* scrolling */


  // w tej metodzie sprawdzamy, czy jesteśmy max 10px mniej niż maxymalna wartość
  // jeśli wartość nie różni się o więcej niż 10 px to zwracamy true
  // i automatycznie scrollujemy na samo dno
  // jeśli jest większ to zwracamy false wtedy będzie trzeba wyświetlić toast,
  // zże przyszły nowe wiadomości (wiadomności te nie mogą być dodane do przeczytanych)
  /*
  possible returned values:
  -1 - element messages does not exist yet.
  0 - false pointer of scroll is has lower value than maxScrollPosition
  1 - message list is scrolled down
  */
  isScrolledDown(): number {
    const mess =  document.getElementById('messages')
    if (mess) {
      if( this.maxScrollPosition <= mess.scrollTop) { return 1 }
      else return 0 
    } else return -1
  }





  private assignScrollTimer(moveDown: boolean) {
    this.scrollInterval = setInterval( () => {
      this.resizeMessageListImmediately()
      const messages = document.getElementById('messages')
      if (messages) {
        messages.scrollTo(0, messages.scrollHeight)
        this.maxScrollPosition = messages.scrollTop
        if (moveDown) this.messageListScrollEventEmitter.emit('down')
        if( this.scrollInterval ) clearInterval( this.scrollInterval )
      }
    }, 50)
  }

  
  
  private scrollDownImmediately() {
    const messages = document.getElementById('messages')
    if (messages) {
      messages.scrollTo(0, messages.scrollHeight)
      this.maxScrollPosition = messages.scrollTop
      this.messageListScrollEventEmitter.emit('down')
    }
  }

  /* 
  Metoda musi uruchamiać interval, który będzie próbował skrolować na dół
  jak tylko obiekt messages będzie dostępny
  jak już tylko będzie dostępny to należy sprawdzić czy:

  
  
  jeśli wchodzimy do chatu po kliknięciu chatu na listę to należy 
  od razu przejść przeskrolować na dół listy 
  niezależnie czy w chatcie były nieprzeczytane wiadomości czy nie
  scrollDown(true)



  Co w przypadku, gdy raz przeskroluje a za chwilę przyjdą kolejne wiadomości. 
  jeśli lista nowych wiadomości jest 0 to 


  */

  scrollDown(moveDown: boolean) {
    if (this.scrollInterval){
      clearInterval( this.scrollInterval )
      this.assignScrollTimer(moveDown)
    } else {
      this.assignScrollTimer(moveDown)
    }
  }




  startMessageListScrollListener() {
    const messageList = document.getElementById('messages')
    if ( messageList ) {
      console.log('message list scrolll emmiter initialized.')
      messageList.addEventListener('scroll', (event) => {
        if ( this.maxScrollPosition <= messageList.scrollTop){
          this.maxScrollPosition = messageList.scrollTop
          this.messageListScrollEventEmitter.emit('down')
        }
        if (messageList.scrollTop == 0){
          this.messageListScrollEventEmitter.emit('top')
        }
      })
    }
  }

  


  stopScrollEmmiter() {
    const messages = document.getElementById('messages')
    if ( messages ){
      messages.removeAllListeners
      console.log('messages scroll listener switch offed')
    }    
  }








  resizeChatListImmediately() {
    const header = document.getElementById('header')
    const chatList = document.getElementById('chat_list')
    if (header && chatList) {
      const h = window.innerHeight - header.offsetHeight 
      chatList.style.height = h + 'px'
    }
  }

  resizeSelectChatImmediately() {
    const header = document.getElementById('header')
    const select = document.getElementById('select_chat')
    if (header && select) {
      const h = window.innerHeight - header.offsetHeight 
      
      select.style.height = h + 'px'
    }  
  }
  
  
  
  
  resizeMessageListAfter(ms: number) {
    if (this.resizeInterval) {
      clearInterval(this.resizeInterval)
      this.resizeInterval = setTimeout(() => {
        this.resizeMessageListImmediately()        
      }, ms)
    } else {
      this.resizeInterval = setTimeout(() => {
        this.resizeMessageListImmediately()        
      }, ms)
    }
  }


  resizeMessageListImmediately(){
    const header = document.getElementById('header')
    const chatHeader = document.getElementById('chat_header')
    const messageList = document.getElementById('messages')
    const sendMessage = document.getElementById('send_message')
    if ( messageList && chatHeader && header && sendMessage ) {
      const h = window.innerHeight - 
        header.offsetHeight -
        chatHeader.offsetHeight - 
        sendMessage.offsetHeight - 16
      messageList.style.height = h + 'px'
      // if we resize window but we are in the bottom of message list
      // we should keep this position during resizing
      if ( this.isScrolledDown() == 1 ) {
        this.scrollDownImmediately()
      }
    } else {
      console.log('header', header)
      console.log('chatHeader', chatHeader)
      console.log('messageList', messageList)
      console.log('sendMessage', sendMessage)
    }
  }










  resizeChatListInterval: NodeJS.Timeout | undefined

  resizeChatList() {
    if (this.resizeChatListInterval) {
      clearInterval(this.resizeChatListInterval)
      this.rcl()
    } else {
      this.rcl()
    }
  }

  private rcl() {
    this.resizeChatListInterval = setInterval(()=>{
      const header = document.getElementById('header')
      const chatList = document.getElementById('chat_list')
      if (header && chatList) {
        const h = window.innerHeight - header.offsetHeight 
        chatList.style.height = h + 'px'
        if (this.resizeChatListInterval) {
          clearInterval(this.resizeChatListInterval)
          this.resizeChatListInterval = undefined
        }
      }
    }, 50)
  }










  resizeSelectChatInterval: NodeJS.Timeout | undefined

  resizeSelectChat() {
    if (this.resizeSelectChatInterval) {
      clearInterval(this.resizeSelectChatInterval)
      this.rsc()
    } else {
      this.rsc()
    }
  }

  private rsc() {
    this.resizeSelectChatInterval = setTimeout( ()=>{
      const header = document.getElementById('header')
      const select = document.getElementById('select_chat')
      // console.warn('wysokosc', h)
      //console.warn('wysokosc', select)
      if (header && select) {
        const h = window.innerHeight - header.offsetHeight - 4
        select.style.height = h + 'px'
        console.warn('wysokosc', h)
        if (this.resizeSelectChatInterval) {
          clearInterval(this.resizeSelectChatInterval)
          this.resizeSelectChatInterval = undefined
        }
      }
    }, 10)
  }



  




}

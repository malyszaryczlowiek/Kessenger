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
      this.resizeMessageList()
      this.resizeChatList()
    })
  }



  closeService() {
    
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





  private assignScrollTimer(emit: boolean) {
    this.scrollInterval = setInterval( () => {
      this.resizeMessageList()
      const messages = document.getElementById('messages')
      if (messages) {
        messages.scrollTo(0, messages.scrollHeight)
        this.maxScrollPosition = messages.scrollTop
        if (emit) this.messageListScrollEventEmitter.emit('down')
        if( this.scrollInterval ) clearInterval( this.scrollInterval )
      }
      console.log('interval')
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

  scrollDown(emit: boolean) {
    if (this.scrollInterval){
      clearInterval( this.scrollInterval )
      this.assignScrollTimer(emit)
    } else {
      this.assignScrollTimer(emit)
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


  resizeChatList() {
    const header = document.getElementById('header')
    const chatList = document.getElementById('chat_list')
    if (header && chatList) {
      const h = window.innerHeight - header.offsetHeight 
      chatList.style.height = h + 'px'
    }
  }
  
  
  
  
  resizeMessageListAfter(ms: number) {
    if (this.resizeInterval) {
      clearInterval(this.resizeInterval)
      this.resizeInterval = setTimeout(() => {
        this.resizeMessageList()        
      }, ms)
    } else {
      this.resizeInterval = setTimeout(() => {
        this.resizeMessageList()        
      }, ms)
    }
  }


  resizeMessageList(){
    const header = document.getElementById('header')
    const chatHeader = document.getElementById('chat_header')
    const messageList = document.getElementById('messages')
    const sendMessage = document.getElementById('send_message')
    if ( messageList && chatHeader && header && sendMessage ) {
      const h = window.innerHeight - 
        header.offsetHeight -
        chatHeader.offsetHeight - 
        sendMessage.offsetHeight 
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




  




}

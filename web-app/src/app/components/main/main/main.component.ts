import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-main',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class MainComponent implements OnInit, OnDestroy {


  /*
    Jak tylko wchodizmy na stronę to UserService sprawdza, czy
    mamy ksid 
      - jeśli tak to wysyła zapytanie do servera czy sesja jest aktywna 
        - jeśli jest aktywna do odsyła wszystkie dane użytkownika,
          które są zapisywne w Userservice i przekierowuje do /user
        - jeśli nie jest aktywna to nie robi nic i czeka aż user się zaloguje
      - jeśli nie mmay ksid bo wygasł to generujemy nowy ksid i wysyłamy 
        jako ciasteczko wraz z login credentials. 


    Jeśli otrzymamy odpowiedź o błędzie serwera to nalezy wyświetlić komunikat,
    że servis jest niedostępny i pozostać na stronie.      
  */
  constructor(private router: Router, private userService: UserService) {
    //here // tutaj jest tworzony userService
    // przenieść go do RootComponent
    // a w ngOnDestroy wywołać czyszczenie userService
    // dzięki temu przy odświerzaniu nie będzie już żadnych niesubskrybowanych subscription
    
  }



  ngOnInit(): void {
    if ( this.userService.user ) 
      this.router.navigate(['user'])
    
    /* this.setHeightLayout()
    this.scrollDown()
    window.addEventListener("resize" , (event) => {
      this.setHeightLayout()
    })  

      // layout tests
    document.getElementById('chat_list')?.addEventListener("scroll", (event) => {
      console.log('SCROLL')
    }); */
  }



  ngOnDestroy(): void {
  }







  /*
    METHODS TO DELETE
  */



  setHeightLayout(){
    const header = document.getElementById('header')
    const chatHeader = document.getElementById('chat_header')
    const messageList = document.getElementById('chat_messages_list')
    const sendMessage = document.getElementById('send_message')
//    const messages = document.getElementById('messages')
    if ( messageList && chatHeader && header && sendMessage ) {
      const h = window.innerHeight - 
        header.offsetHeight -
        chatHeader.offsetHeight - 
        sendMessage.offsetHeight 
      messageList.style.height = h + 'px'
    }
  }

  scrollDown() {
    const messages = document.getElementById('messages')
    if (messages) {
      messages.scrollTo(0, messages.scrollHeight)
    }
  }



  moveFocus() {
    const h = document.getElementById('chat_list')?.scrollTop // to jest wysokość na jakiej znajduje się pointer w scrollu
    const hh = document.getElementById('chat_list')?.scrollHeight 
    
    console.log('height: ', h, hh)
    const b = document.getElementById('hidden_end')?.hidden
    console.log('hidden: ', b)
    console.log('device height: ', window.innerHeight)
    document.getElementById('signin-pass')?.hidden

    const chatList = document.getElementById('chat_list')
    if (chatList ) {
      chatList.scrollTo(0,chatList.scrollHeight)
      console.log('new scrolled height: ', chatList.scrollHeight, chatList.scrollTop)
    }

    console.log('height of header', document.getElementById('header')?.offsetHeight)

    // rekalkulacja wysokości rowów w tabeli
    // window.addEventListener("resize", reportWindowSize);

    // to jest wysokość okna
    window.innerHeight

    
  }

}

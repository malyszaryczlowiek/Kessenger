import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ChatData } from 'src/app/models/ChatData';
import { UserService } from 'src/app/services/user.service';



@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.css']
})
export class UserComponent implements OnInit {

  public chats: Array<ChatData> = new Array();

  constructor(private userService: UserService, private router: Router) { }

  ngOnInit(): void {
    if ( this.userService.isSessionValid() ){
      this.chats = this.userService.chatAndUsers;
    } else {
      this.router.navigate(['/session-timeout']);
    }    
  }

  // przepisać wyszstkie metody z ConnectionService do UserService tak aby można było wykonywać rządania
  
  // a  następnie tutaj zaimplementować fetchowanie danych z servera jesli nie 
  // ma ich jeszcze w userSerivece

  // oraz  napisać pętlę sprawdzającą czy dalej jest ciasko 
  // a jak już nie będzie to należy wylogować użytkownika i p
  // przenieść go do strony logoawania 






  /* fetch() {
    if ( userId ) {
      this.connection.updateSession(userId);
      console.log('KSID exists') 
      // we get user's settings 
      const s = this.connection.user(userId)
      if ( s ) {
        s.subscribe({
          next: (response) => {
            const body = response.body
            if ( body ){
              this.user = body.user
              this.settings.setSettings(body.settings)
              // this.connectViaWebsocket()
            }
            else {
              // print error message
              console.log(`ERROR /user/userId/settings: `)
            }
          },
          error: (error) => {
            console.log(error) 
            this.clearService()
            console.log('redirection to logging page')
            this.router.navigate([''])
          },
          complete: () => {}
        })
      }


      // we get user's chats
      const c = this.connection.getChats(userId);
      if ( c ){
        c.subscribe({
          next: (response) => {
            // we need to sort our chats according to messageTime
            const chats = response.body 
            // we sort newest (larger lastMessageTime) first.
            if (chats) {
              this.chatAndUsers = chats.sort((a,b) => -(a.chat.lastMessageTime - b.chat.lastMessageTime))
              .map((item, i, array) => {
                return {
                  chat: item.chat,
                  partitionOffsets: item.partitionOffsets,
                  users: item.users,
                  messages: item.messages
                };
              });
            }
            // redirect to /user
            this.router.navigate(['user']);
  
          } ,
          error: (error) => {
            console.log(error) 
            this.clearService()
            console.log('redirection to logging page')
            this.router.navigate([''])
          } ,
          complete: () => {}
        })
      }
    }
  } */




}

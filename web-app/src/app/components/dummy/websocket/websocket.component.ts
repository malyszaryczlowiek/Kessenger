import { Component, OnDestroy, OnInit } from '@angular/core';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-websocket',
  templateUrl: './websocket.component.html',
  styleUrls: ['./websocket.component.css']
})
export class WebsocketComponent implements OnInit, OnDestroy {

  private num: number = 0;
  socket : WebSocket | undefined;
  private mess: string = 'empty message'

  constructor(private userService: UserService) { 

  }

  ngOnInit(): void {
    // this.userService.callAngular();
    // this.userService.connectViaWebsocket();
    this.socket = new WebSocket("ws://localhost:9000/angular/ws/info");
    this.socket.onopen = function(e) {
      console.log('connected via WebSocket.')
      //this.socket.send("My name is John");
    };
    this.socket.onerror = function (error) {
      console.log('WEBSOCKET ERROR, cannot connect to server', error)
    }

   }

  ngOnDestroy(): void {
    // this.userService.closeWS(); 

    if (this.socket) {
      this.socket.close()
      console.log('socket closed.')
    }
  }


  sendMessage(msg: string) {
    // this.userService.sendMessage(msg);
  }

  sendMess() {
    //this.userService.callAngular();
    // this.sendMessage(`Message num ${++this.num}.`)
    if (this.socket)
      this.socket.send("Wiadomość");
    else 
      console.log('socket jest niezdefiniowany')
  }

  sendToClose() {
    if (this.socket)
      this.socket.send("exit");
  }

}

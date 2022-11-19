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

  constructor(private userService: UserService) { 

  }

  ngOnInit(): void {
    // this.userService.callAngular();
    // this.userService.connectViaWebsocket();
    this.socket = new WebSocket("ws://localhost:9000/angular/ws/info");
    this.socket.onopen = function(e) {
      alert("[open] Connection established");
      alert("Sending to server");
      //this.socket.send("My name is John");
    };
   }

  ngOnDestroy(): void {
    // this.userService.closeWS(); 
    if (this.socket) {
      this.socket.close()
    }
  }


  sendMessage(msg: string) {
    // this.userService.sendMessage(msg);
  }

  sendMess() {
    //this.userService.callAngular();
    // this.sendMessage(`Message num ${++this.num}.`)
    if (this.socket)
      this.socket.send("My name is John");
    else 
      console.log('socket jest niezdefiniowany')
  }

}

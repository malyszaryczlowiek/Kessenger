import { Component, OnDestroy, OnInit } from '@angular/core';
import { HtmlService } from 'src/app/services/html.service';
import { UserService } from 'src/app/services/user.service';



@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.css']
})
export class UserComponent implements OnInit, OnDestroy {


  constructor(private userService: UserService, private htmlService: HtmlService) { }
  
  ngOnInit(): void {
    console.log('UserComponent.ngOnInit()')
    if ( ! this.userService.isWSconnectionDefined() ) this.userService.connectViaWebsocket() 
  }


  ngOnDestroy(): void {
    console.log('UserComponent.ngOnDestroy()')
  }


}

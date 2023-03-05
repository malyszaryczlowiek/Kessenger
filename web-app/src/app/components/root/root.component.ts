import { Component, OnDestroy, OnInit } from '@angular/core';
import { UserService } from 'src/app/services/user.service';


@Component({
  selector: 'app-root',
  templateUrl: './root.component.html',
  styleUrls: ['./root.component.css']
})
export class RootComponent implements OnInit, OnDestroy {

  constructor(private userService: UserService) { }

  ngOnInit(): void {
    console.log('RootComponent.ngOnInit()')
  }

  ngOnDestroy(): void {
    // if user is defined, we need clear all data.
    // it is required during page reload, because possible 
    // data and resources leakage. 
    console.log('RootComponent.ngOnDestroy()')
    if (this.userService.user) this.userService.clearService()
  }

}

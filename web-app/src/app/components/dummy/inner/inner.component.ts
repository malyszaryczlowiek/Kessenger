import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Observable } from 'rxjs';
import { User } from 'src/app/models/User';

@Component({
  selector: 'app-inner',
  templateUrl: './inner.component.html',
  styleUrls: ['./inner.component.css']
})
export class InnerComponent implements OnInit , OnDestroy{

  @Input() user: User | undefined // = {user_id:'', login: ''} // empty user
  @Output() remover: EventEmitter<User> = new EventEmitter()
  userr$: Observable<User> = new Observable

  constructor() { 
    console.log('inner constructor called.')

  }

  ngOnInit(): void {
    console.log('inner onInit Called ')
  }

  ngOnDestroy(): void {
    console.log('inner destroyed')
  }

  removeThisUser() {
    this.remover.emit(this.user);
    // this.user = undefined;
  }

}

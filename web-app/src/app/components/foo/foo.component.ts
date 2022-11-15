import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { User } from 'src/app/models/User';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-foo',
  templateUrl: './foo.component.html',
  styleUrls: ['./foo.component.css']
})
export class FooComponent implements OnInit {

  // public users: User[] =  new Array<User>();
  public users$: Observable<User[]> = new Observable;

  public postMessage$: Observable<string> = new Observable


  constructor(private userService: UserService) { }

  ngOnInit(): void {
    // this.users = new Array<User>();
  }

  getUsers() {
    console.log('button clicked');
    this.users$ = this.userService.getUsers();
    console.log('sevice requested');
    console.log('users assigned.');
  }

  postNothing() {
     this.userService.postNothing().subscribe(message => this.postMessage$ = of(message))
  }

}

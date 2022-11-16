import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { User } from 'src/app/models/User';
import { UserService } from 'src/app/services/user.service';
import { UtctimeService } from 'src/app/services/utctime.service';

@Component({
  selector: 'app-foo',
  templateUrl: './foo.component.html',
  styleUrls: ['./foo.component.css']
})
export class FooComponent implements OnInit {

  // public users: User[] =  new Array<User>();
  public users$: Observable<User[]> = new Observable;

  public postMessage$: Observable<string> = new Observable


  constructor(private userService: UserService, private utcTime: UtctimeService) { }

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

  postUser() {
    this.userService.postUser().subscribe({
      next: (restp) => console.log('returned' + restp),
      error: (error) => console.error(error),
      complete: () => console.log('json post completed.')
    })
  }

  cookie: string | undefined;

  generateCookie() {
    this.cookie = this.userService.createKSID()
  }

  getKSID() {
    this.cookie = this.userService.getRawKSID();
  }

}

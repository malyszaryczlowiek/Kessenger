import { compileDirectiveFromMetadata } from '@angular/compiler';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { concat, map, Observable, reduce, zip, zipAll, zipWith } from 'rxjs';
import { User } from 'src/app/models/User';
import { ConnectionService } from 'src/app/services/connection.service';

@Component({
  selector: 'app-first',
  templateUrl: './first.component.html',
  styleUrls: ['./first.component.css']
})
export class FirstComponent implements OnInit, OnDestroy {

  show: boolean = true;
  public users$: Observable<User[]> = new Observable
  users: User[] = new Array()
  
  constructor(private connection: ConnectionService) { }

  ngOnInit(): void {
    console.log('first component initalized')
  }

  ngOnDestroy(): void {
    console.log('first component destroyed')
  }

  showHide() {
    console.log('hide clicked')
    this.show = !this.show;
  }

  downloadUsers() {
    this.users$ = this.connection.getStream()
    console.log('po przypisaniu do observable<user[]> ale przed subscribe')
    this.users$.subscribe({
      next: array => {
        console.log('in subscribe array processing ');
        this.users = array
      },
      error: error => console.error(error), // `error w subscribe(): ${error}`
      complete: () => console.log('subscribe completed normally. ')
    })
    console.log('Observable po subscribe() ')
  }

  getUsers() {
    this.connection.getUsers()
  }

  removeUser(user: User, i: number) {


    this.users =  this.users.filter((u,index, array) => {
      let bool = u.userId !== user.userId ;
      console.log(`boolean is ${bool}`);
      return bool;
      //array.push(u)      
    } )
    console.log(`size of array: ${this.users.length}`)
  }
    /*
    this.users$.pipe( // this.users$ = 
      map(array => array.filter( (u) => {
        let bool = u.user_id !== user.user_id 
        console.log(`boolean is ${bool}`)
        bool
      } ) )
    ).subscribe({
      next: array => console.log('Delete processing'),
      error: error => console.error(error), // `error w subscribe(): ${error}`
      complete: () => console.log('subscribe during DELETING completed normally. ')
      
    })
     this.users$.subscribe({
      next: array => console.log('Delete processing'),
      error: error => console.error(error), // `error w subscribe(): ${error}`
      complete: () => console.log('subscribe during DELETING completed normally. ')
      
    }) */



}


/*
reduce((acc, user) => {
        if (acc.length === 0) {
          let folded = new Array<User>();
          folded.push(user)
          return folded
        } else {
          acc.push(user)
          return acc
        }

      })
       */
import { Injectable } from '@angular/core';
import { environment } from './../../environments/environment';
import { Server } from '../models/Server';

@Injectable({
  providedIn: 'root'
})
export class LoadBalancerService {

  currentServer: Server | undefined

  working: Array<Server> = new Array<Server>()
  //crashed: Array<Server> = new Array<Server>()

  constructor() {
    console.log('LoadBalancerService initialization.')
    console.log('environment prod: ', environment.production)
    console.log('environment numberOfServers: ', environment.numOfServers)
    this.assignServers()
    this.selectNewServer()
   }

   


   assignServers() {
    let i = 0
    while (i < environment.numOfServers ) {
      console.warn('assigning server', i)
      const s = new Server(environment.protocol, environment.domainName, environment.port + i)
      this.working.push( s )
      i = i + 1
    }
   }


   

  rebalance() {
    const serv = this.currentServer
    if ( serv ) {
      this.working = this.working.filter((s,i, arr) => {
        return s != serv;
      })
      // if (this.currentServer) this.crashed.push(this.currentServer)
      if (this.working.length > 0) this.selectNewServer() 
      else this.assignServers()
    }    
  }

  
  
  selectNewServer() {
    const indx = Math.floor(Math.random() * this.working.length)
    this.currentServer = this.working.at( indx )
  }



}

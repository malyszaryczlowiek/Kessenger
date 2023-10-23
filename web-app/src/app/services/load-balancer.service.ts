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
    console.log('LoadBalancerService.constructor().')
    console.log('LoadBalancerService.constructor() -> env prod: ', environment.production)
    console.log('LoadBalancerService.constructor() -> environment numberOfServers: ', environment.numOfServers)
    this.assignServers()
    this.selectNewServer()
  }

   


  assignServers() {
    let i = 0
    console.log('LoadBalancerService.assignServers().')
    while (i < environment.numOfServers ) {
      console.log(`LoadBalancerService.assignServers() -> assigning server: ${i} `)
      const s = new Server(environment.protocol, environment.domainName, environment.port + i)
      this.working.push( s )
      i = i + 1
    }
  }


   

  rebalance() {
    console.log(`LoadBalancerService.rebalance() `)
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
    console.log(`LoadBalancerService.selectNewServer() `)
    const indx = Math.floor(Math.random() * this.working.length)
    this.currentServer = this.working.at( indx )
  }



}



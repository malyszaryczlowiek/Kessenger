import { Injectable } from '@angular/core';
import { Server } from '../models/Server';

@Injectable({
  providedIn: 'root'
})
export class LoadBalancerService {

  currentServer: Server | undefined

  working: Array<Server> = new Array<Server>()
  crashed: Array<Server> = new Array<Server>()

  constructor() { }


  rebalance(): boolean {
    if (this.working.length == 0) {
      return false;
    } else {
      // here we recalculate new server
      return  true
    }    
  }

}

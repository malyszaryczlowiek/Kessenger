export class Server {

    constructor(domain: string, port: number){
      this.domain = domain;
      this.port = port;
    }  
  
    private domain: string;  
    private port: number;
    
  
    getURI() {
      return `${this.domain}:${this.port}/`;
    }
  
  
  
  };
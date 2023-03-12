export class Server {

    constructor(protocol: string, domain: string, port: number){
      this.protocol = protocol;
      this.domain = domain;
      this.port = port;
    }  
  
    private protocol: string;
    private domain: string;  
    private port: number;
    
  
    getURI() {
      return `${this.protocol}://${this.domain}:${this.port}`;
    }

    getURIwithoutProtocol() {
      return `${this.domain}:${this.port}`;
    }


    getURIwithPath(path: string) {
      return `${this.protocol}://${this.domain}:${this.port}${path}`;
    }

  
  
  
  };
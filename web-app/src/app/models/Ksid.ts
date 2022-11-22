 export class Ksid {

  constructor(sessId: string, userId: string, validityTime: number){
    this.sessId = sessId;
    this.userId = userId;
    this.validityTime = validityTime;
  }  

  public sessId: string;  
  public userId: string;
  public validityTime: number;
  
  

  toString() {
    return `${this.sessId}__${this.userId}__${this.validityTime}`;
  }



};
    

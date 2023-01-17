import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ConnectionService } from 'src/app/services/connection.service';
import { UserService } from 'src/app/services/user.service';
import { CookieService } from 'ngx-cookie-service';

@Component({
  selector: 'app-main',
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css']
})
export class MainComponent implements OnInit {


  /*
    Jak tylko wchodizmy na stronę to UserService sprawdza, czy
    mamy ksid 
      - jeśli tak to wysyła zapytanie do servera czy sesja jest aktywna 
        - jeśli jest aktywna do odsyła wszystkie dane użytkownika,
          które są zapisywne w Userservice i przekierowuje do /user
        - jeśli nie jest aktywna to nie robi nic i czeka aż user się zaloguje
      - jeśli nie mmay ksid bo wygasł to generujemy nowy ksid i wysyłamy 
        jako ciasteczko wraz z login credentials. 


    Jeśli otrzymamy odpowiedź o błędzie serwera to nalezy wyświetlić komunikat,
    że servis jest niedostępny i pozostać na stronie.      
  */
  constructor(private router: Router, private userService: UserService) {
    here // tutaj jest tworzony userService
    // przenieść go do RootComponent
    // a w ngOnDestroy wywołać czyszczenie userService
    // dzięki temu przy odświerzaniu nie będzie już żadnych niesubskrybowanych subscription
    
  }

  ngOnInit(): void {
    if ( this.userService.user ) 
      this.router.navigate(['user'])
  }

}

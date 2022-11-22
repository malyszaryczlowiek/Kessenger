import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-create-chat',
  templateUrl: './create-chat.component.html',
  styleUrls: ['./create-chat.component.css']
})
export class CreateChatComponent implements OnInit {

  // tutaj form z danymi do chatu.
  
  
  
  constructor(private userService: UserService) { }

  ngOnInit(): void {

  }


  // to jest po naciśnięciu buttonu submit
  // tutaj wysyłamy rządanie utworzenia czatu
  // jeśli w odpowiedzi dostaniwmy dane z chatem to 
  // w userService należy zaktualizować listę czatów
  create() {

  }


  // metoda do szukania użytkownika w bazie danych 
  search(login: string) {

  }



}

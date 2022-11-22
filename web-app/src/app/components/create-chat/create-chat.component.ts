import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { User } from 'src/app/models/User';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-create-chat',
  templateUrl: './create-chat.component.html',
  styleUrls: ['./create-chat.component.css']
})
export class CreateChatComponent implements OnInit {

  // tutaj form z danymi do chatu.
  
  public foundUsers = [
    {
      login: "log1",
      userId: "uuid1"
    },
    {
      login: "log2",
      userId: "uuid2"
    }
  ];//new Array<User>();
  public selectedUsers = new Array<User>();

  form = new FormGroup({
    chatName: new FormControl('',[Validators.required, Validators.minLength(3)])
    //groupChat: new FormControl(false),
  });

  public disableSubmitting: boolean = true;
  
  constructor(private userService: UserService) { }

  ngOnInit(): void {
    

  }


  // to jest po naciśnięciu buttonu submit
  // tutaj wysyłamy rządanie utworzenia czatu
  // jeśli w odpowiedzi dostaniwmy dane z chatem to 
  // w userService należy zaktualizować listę czatów
  create() {
    // tutaj musimy utworzyć obiekt do wysłania na server
    //objectToSend: ;
    //this.userService.createChat()
    // albo createGroupChat() sprawdzić w backendzie

  }





  // metoda do szukania użytkownika w bazie danych 
  search(login: string) {
    // make array empty
    this.foundUsers = new Array<User>();

    // and fill it with new found one

  }
  
  
  
  addToSelected(u: User) {
    this.selectedUsers.push(u);
    // and remove from found
    this.foundUsers = this.foundUsers.filter( (user, index, array) => {
      return user.userId != u.userId;
    });
    this.validateForm();
  }

  
  
  
  unselect(u: User) {
    this.selectedUsers = this.selectedUsers.filter((user, index, array) => {
      return u.userId != user.userId
    })
    this.foundUsers.push(u);
    this.validateForm();
  }

  validateForm() {
    this.disableSubmitting = !( this.form.valid && this.selectedUsers.length > 0 );
  }


}

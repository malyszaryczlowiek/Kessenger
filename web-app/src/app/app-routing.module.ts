import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CreateChatComponent } from './components/create-chat/create-chat.component';
import { ChildAComponent } from './components/dummy/child-a/child-a.component';
import { ChildBComponent } from './components/dummy/child-b/child-b.component';
import { FirstComponent } from './components/dummy/first/first.component';
import { SecondComponent } from './components/dummy/second/second.component';
import { WebsocketComponent } from './components/dummy/websocket/websocket.component';
import { MainComponent } from './components/main/main/main.component';
import { PagenotfoundComponent } from './components/pagenotfound/pagenotfound.component';
import { ChatPanelComponent } from './components/user/chat/chat-panel/chat-panel.component';
import { EditAccountComponent } from './components/user/edit-account/edit-account.component';
import { EditChatSettingsComponent } from './components/user/edit-chat-settings/edit-chat-settings.component';
import { UserComponent } from './components/user/user/user.component';

const firstComponentChildRoutes: Routes = [
  {path: 'child-a', component: ChildAComponent},
  {path: 'child-b', component: ChildBComponent}
];





const routes: Routes = [
  {path: '', component: MainComponent},
  {
    path: 'user', 
    component: UserComponent,
    children: [
      {path: 'chat/:chatId', component: ChatPanelComponent},
      {path: 'editChat/:chatId', component: EditChatSettingsComponent}
    ]
  },
  {path: 'user/settings', component: EditAccountComponent},
  {path: 'user/createChat', component: CreateChatComponent},



  {path: 'first-component', component: FirstComponent },
  { 
    path: 'second-component', 
    component: SecondComponent ,
    children: firstComponentChildRoutes
  },
  {path: 'websocket-component', component: WebsocketComponent },
  // {path: '', component: },
  {path: '**', component: PagenotfoundComponent},
];



@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }


/*

TODO sprawdzić jak można przekazywać dane z parenta i childa w routingu

*/
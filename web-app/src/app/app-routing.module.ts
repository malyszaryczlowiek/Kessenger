import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CreateChatComponent } from './components/create-chat/create-chat.component';
import { MainComponent } from './components/main/main/main.component';
import { PagenotfoundComponent } from './components/pagenotfound/pagenotfound.component';
import { RootComponent } from './components/root/root.component';
import { SessionTimeoutComponent } from './components/session-timeout/session-timeout.component';
import { ChatPanelComponent } from './components/user/chat/chat-panel/chat-panel.component';
import { ChatComponent } from './components/user/chat/chat/chat.component';
import { SelectChatComponent } from './components/user/chat/select-chat/select-chat.component';
import { EditAccountComponent } from './components/user/edit-account/edit-account.component';
import { EditChatSettingsComponent } from './components/user/edit-chat-settings/edit-chat-settings.component';
import { UserComponent } from './components/user/user/user.component';

const firstComponentChildRoutes: Routes = [
  //{path: 'child-a', component: ChildAComponent},
  // {path: 'child-b', component: ChildBComponent}
];



const routes: Routes = [
  { path: '', 
    component: RootComponent,
    children: [
      {path: '', component: MainComponent},
      {
        path: 'user', 
        component: UserComponent,
        children: [
          {path: '', 
          component: ChatComponent,
          children: [
            {path: '', component: SelectChatComponent},
            {path: 'chat/:chatId', component: ChatPanelComponent},
            {path: 'editChat/:chatId', component: EditChatSettingsComponent}
            ]
          },
          {path: 'settings',   component: EditAccountComponent},
          {path: 'createChat', component: CreateChatComponent}
        ]
      },
      {path: 'session-timeout', component: SessionTimeoutComponent},
      {path: '**', component: PagenotfoundComponent}
    ]
  }
];



@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }


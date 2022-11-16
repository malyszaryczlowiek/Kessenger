import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http'

import { CookieService } from 'ngx-cookie-service';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { FooComponent } from './components/foo/foo.component';
import { MainComponent } from './components/main/main/main.component';
import { SigninComponent } from './components/main/signin/signin.component';
import { SignupComponent } from './components/main/signup/signup.component';
import { UserComponent } from './components/user/user/user.component';
import { PagenotfoundComponent } from './components/pagenotfound/pagenotfound.component';
import { CreateChatComponent } from './components/create-chat/create-chat.component';
import { HeaderComponent } from './components/user/header/header.component';
import { EditAccountComponent } from './components/user/edit-account/edit-account.component';
import { EditChatSettingsComponent } from './components/user/edit-chat-settings/edit-chat-settings.component';
import { ChatListComponent } from './components/user/chat/chat-list/chat-list.component';
import { ChatListItemComponent } from './components/user/chat/chat-list-item/chat-list-item.component';
import { ChatPanelComponent } from './components/user/chat/chat-panel/chat-panel.component';
import { SendMessageComponent } from './components/user/chat/send-message/send-message.component';
import { MessageListComponent } from './components/user/chat/message-list/message-list.component';
import { MessageItemComponent } from './components/user/chat/message-item/message-item.component';

@NgModule({
  declarations: [
    AppComponent,
    FooComponent,
    MainComponent,
    SigninComponent,
    SignupComponent,
    UserComponent,
    PagenotfoundComponent,
    CreateChatComponent,
    HeaderComponent,
    ChatListComponent,
    ChatListItemComponent,
    EditAccountComponent,
    EditChatSettingsComponent,
    ChatPanelComponent,
    SendMessageComponent,
    MessageListComponent,
    MessageItemComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule, 
    HttpClientModule, 
    
  ],
  providers: [
    {
      provide:  "API_URL", 
      useValue: "http://localhost:9000"
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

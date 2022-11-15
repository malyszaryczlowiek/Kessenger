import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http'

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { FooComponent } from './components/foo/foo.component';

@NgModule({
  declarations: [
    AppComponent,
    FooComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule, 
    HttpClientModule
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

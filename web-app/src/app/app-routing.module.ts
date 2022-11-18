import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ChildAComponent } from './components/dummy/child-a/child-a.component';
import { ChildBComponent } from './components/dummy/child-b/child-b.component';
import { FirstComponent } from './components/dummy/first/first.component';
import { SecondComponent } from './components/dummy/second/second.component';
import { MainComponent } from './components/main/main/main.component';
import { PagenotfoundComponent } from './components/pagenotfound/pagenotfound.component';
import { UserComponent } from './components/user/user/user.component';

const routes: Routes = [
  {path: '', component: MainComponent},
  {path: 'user', component: UserComponent},
  {path: 'first-component', component: FirstComponent },
  { 
    path: 'second-component', 
    component: SecondComponent ,
    children: [
      {path: 'child-a', component: ChildAComponent},
      {path: 'child-b', component: ChildBComponent},
    ]  
  },
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
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditChatSettingsComponent } from './edit-chat-settings.component';

describe('EditChatSettingsComponent', () => {
  let component: EditChatSettingsComponent;
  let fixture: ComponentFixture<EditChatSettingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ EditChatSettingsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EditChatSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

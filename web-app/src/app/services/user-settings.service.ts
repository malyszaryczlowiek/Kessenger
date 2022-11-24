import { Injectable } from '@angular/core';
import { Settings } from '../models/Settings';

@Injectable({
  providedIn: 'root'
})
export class UserSettingsService {

  public settings: Settings = {
    joiningOffset: 0,
    sessionDuration: 900000, // 15 min 
    zoneId: "UTC"
  };

  constructor() { }

  setSettings(s: Settings) {
    this.settings = s;
  }

  
}

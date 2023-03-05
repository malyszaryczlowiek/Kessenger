import { TestBed } from '@angular/core/testing';

import { ChatsDataService } from './chats-data.service';

describe('ChatsDataService', () => {
  let service: ChatsDataService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatsDataService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

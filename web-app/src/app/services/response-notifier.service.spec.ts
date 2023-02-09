import { TestBed } from '@angular/core/testing';

import { ResponseNotifierService } from './response-notifier.service';

describe('ResponseNotifierService', () => {
  let service: ResponseNotifierService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ResponseNotifierService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

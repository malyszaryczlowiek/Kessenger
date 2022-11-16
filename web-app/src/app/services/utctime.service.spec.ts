import { TestBed } from '@angular/core/testing';

import { UtctimeService } from './utctime.service';

describe('UtctimeService', () => {
  let service: UtctimeService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(UtctimeService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

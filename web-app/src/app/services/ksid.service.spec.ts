import { TestBed } from '@angular/core/testing';

import { KsidService } from './ksid.service';

describe('KsidService', () => {
  let service: KsidService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(KsidService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

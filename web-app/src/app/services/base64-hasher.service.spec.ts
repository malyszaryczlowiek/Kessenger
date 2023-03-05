import { TestBed } from '@angular/core/testing';

import { Base64HasherService } from './base64-hasher.service';

describe('Base64HasherService', () => {
  let service: Base64HasherService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Base64HasherService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

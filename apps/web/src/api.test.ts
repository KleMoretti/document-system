import { describe, expect, it } from 'vitest';
import { joinUrl, tokenHeader } from './api';

describe('api utilities', () => {
  it('joins base urls without duplicate slashes', () => {
    expect(joinUrl('http://localhost:8080/', '/api/me')).toBe('http://localhost:8080/api/me');
    expect(joinUrl('http://localhost:8080', 'api/me')).toBe('http://localhost:8080/api/me');
  });

  it('builds bearer headers when token exists', () => {
    expect(tokenHeader('abc')).toEqual({ Authorization: 'Bearer abc' });
    expect(tokenHeader(null)).toEqual({});
  });
});

import { describe, expect, it } from 'vitest';
import { websocketBaseFromApiBase } from './config';

describe('frontend config', () => {
  it('derives websocket base from http api base', () => {
    expect(websocketBaseFromApiBase('http://localhost:8081')).toBe('ws://localhost:8081');
    expect(websocketBaseFromApiBase('https://docs.example.com/api')).toBe('wss://docs.example.com/api');
  });
});

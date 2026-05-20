import { describe, expect, it } from 'vitest';
import { nextReconnectDelay, shouldReconnectAfterSocketError } from './CollaborativeEditor';

describe('CollaborativeEditor websocket error handling', () => {
  it('does not reconnect after authentication or authorization errors', () => {
    expect(shouldReconnectAfterSocketError('UNAUTHORIZED')).toBe(false);
    expect(shouldReconnectAfterSocketError('FORBIDDEN')).toBe(false);
    expect(shouldReconnectAfterSocketError('SLOW_CLIENT')).toBe(true);
  });

  it('caps reconnect attempts after repeated failures', () => {
    expect(nextReconnectDelay(0)).toEqual({ delay: 1000, nextAttempts: 1, shouldReconnect: true });
    expect(nextReconnectDelay(12)).toEqual({ delay: 0, nextAttempts: 12, shouldReconnect: false });
  });
});

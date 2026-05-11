import { describe, expect, it } from 'vitest';
import { shouldReconnectAfterSocketError } from './CollaborativeEditor';

describe('CollaborativeEditor websocket error handling', () => {
  it('does not reconnect after authentication or authorization errors', () => {
    expect(shouldReconnectAfterSocketError('UNAUTHORIZED')).toBe(false);
    expect(shouldReconnectAfterSocketError('FORBIDDEN')).toBe(false);
    expect(shouldReconnectAfterSocketError('SLOW_CLIENT')).toBe(true);
  });
});

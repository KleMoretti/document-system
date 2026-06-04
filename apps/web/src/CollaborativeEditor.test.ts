import { describe, expect, it } from 'vitest';
import * as Y from 'yjs';
import {
  applySyncInitUpdates,
  nextReconnectDelay,
  shouldReconnectAfterSocketError,
  shouldSubmitSnapshot
} from './CollaborativeEditor';

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

  it('applies websocket snapshots before incremental updates', () => {
    const source = new Y.Doc();
    source.getMap('metadata').set('title', 'snapshot');
    const snapshot = bytesToBase64(Y.encodeStateAsUpdate(source));
    source.getMap('metadata').set('title', 'latest');
    const update = bytesToBase64(Y.encodeStateAsUpdate(source));

    const target = new Y.Doc();
    applySyncInitUpdates(target, { snapshot, updates: [update] });

    expect(target.getMap('metadata').get('title')).toBe('latest');
  });

  it('submits snapshots only for editable documents with enough incremental updates', () => {
    expect(shouldSubmitSnapshot({ readOnly: false, updatesCount: 100, snapshotSeq: 0 })).toBe(true);
    expect(shouldSubmitSnapshot({ readOnly: true, updatesCount: 100, snapshotSeq: 0 })).toBe(false);
    expect(shouldSubmitSnapshot({ readOnly: false, updatesCount: 20, snapshotSeq: 0 })).toBe(false);
  });
});

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

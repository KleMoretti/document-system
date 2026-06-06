import { afterEach, describe, expect, it, vi } from 'vitest';
import * as Y from 'yjs';
import {
  applySyncInitUpdates,
  createUpdateBatcher,
  nextReconnectDelay,
  shouldSendRealtimeMessage,
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

describe('CollaborativeEditor websocket write batching', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('merges local Yjs updates into one websocket payload per flush window', () => {
    vi.useFakeTimers();
    const updates = collectTextUpdates('ab');
    const sent: Uint8Array[] = [];
    const batcher = createUpdateBatcher({
      flushDelayMs: 35,
      send: (update) => sent.push(update)
    });

    batcher.enqueue(updates[0]);
    batcher.enqueue(updates[1]);
    vi.advanceTimersByTime(34);
    expect(sent).toHaveLength(0);

    vi.advanceTimersByTime(1);

    expect(sent).toHaveLength(1);
    const target = new Y.Doc();
    Y.applyUpdate(target, sent[0]);
    expect(target.getText('body').toString()).toBe('ab');
  });

  it('holds critical updates while websocket bufferedAmount is too high', () => {
    vi.useFakeTimers();
    const [update] = collectTextUpdates('a');
    const sent: Uint8Array[] = [];
    let canSend = false;
    const batcher = createUpdateBatcher({
      flushDelayMs: 35,
      retryDelayMs: 20,
      canSend: () => canSend,
      send: (payload) => sent.push(payload)
    });

    batcher.enqueue(update);
    vi.advanceTimersByTime(35);
    expect(sent).toHaveLength(0);

    canSend = true;
    vi.advanceTimersByTime(20);

    expect(sent).toHaveLength(1);
  });

  it('drops non-critical realtime messages when socket backpressure is high', () => {
    expect(shouldSendRealtimeMessage(1024, 2048)).toBe(true);
    expect(shouldSendRealtimeMessage(4096, 2048)).toBe(false);
  });
});

function collectTextUpdates(value: string): Uint8Array[] {
  const doc = new Y.Doc();
  const updates: Uint8Array[] = [];
  doc.on('update', (update: Uint8Array) => updates.push(update));
  Array.from(value).forEach((char) => doc.getText('body').insert(doc.getText('body').length, char));
  return updates;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

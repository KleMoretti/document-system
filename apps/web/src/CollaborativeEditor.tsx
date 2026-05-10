import { useEffect, useMemo, useState } from 'react';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import Placeholder from '@tiptap/extension-placeholder';
import * as Y from 'yjs';

const WS_BASE = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8080';

type SocketMessage = {
  type: string;
  docId: string;
  userId?: string;
  update?: string;
  updates?: string[];
  displayName?: string;
  color?: string;
  code?: string;
  message?: string;
};

type Props = {
  docId: string;
  token: string;
  readOnly: boolean;
  displayName: string;
};

export function CollaborativeEditor({ docId, token, readOnly, displayName }: Props) {
  const ydoc = useMemo(() => new Y.Doc(), [docId]);
  const [status, setStatus] = useState<'connecting' | 'connected' | 'offline'>('connecting');
  const [online, setOnline] = useState<string[]>([]);

  const editor = useEditor(
    {
      editable: !readOnly,
      extensions: [
        StarterKit.configure({ undoRedo: false }),
        Placeholder.configure({ placeholder: readOnly ? '只读文档' : '开始编辑文档...' }),
        Collaboration.configure({ document: ydoc })
      ],
      editorProps: {
        attributes: {
          class: 'editor-surface',
          'aria-label': '文档编辑器'
        }
      }
    },
    [ydoc, readOnly]
  );

  useEffect(() => {
    editor?.setEditable(!readOnly);
  }, [editor, readOnly]);

  useEffect(() => {
    const socket = new WebSocket(`${WS_BASE}/ws/documents/${docId}?token=${encodeURIComponent(token)}`);

    socket.addEventListener('open', () => {
      setStatus('connected');
      socket.send(
        JSON.stringify({
          type: 'presence:update',
          displayName,
          color: colorFor(displayName)
        })
      );
    });

    socket.addEventListener('close', () => setStatus('offline'));
    socket.addEventListener('error', () => setStatus('offline'));

    socket.addEventListener('message', (event) => {
      const msg = JSON.parse(event.data as string) as SocketMessage;
      if (msg.type === 'sync:init') {
        msg.updates?.forEach((update) => Y.applyUpdate(ydoc, base64ToBytes(update), 'remote'));
      }
      if (msg.type === 'sync:update' && msg.update) {
        Y.applyUpdate(ydoc, base64ToBytes(msg.update), 'remote');
      }
      if (msg.type === 'presence:update' && msg.displayName) {
        setOnline((current) => Array.from(new Set([...current, msg.displayName!])));
      }
      if (msg.type === 'error') {
        setStatus('offline');
      }
    });

    const onUpdate = (update: Uint8Array, origin: unknown) => {
      if (origin === 'remote' || socket.readyState !== WebSocket.OPEN || readOnly) {
        return;
      }
      socket.send(JSON.stringify({ type: 'sync:update', update: bytesToBase64(update) }));
    };
    ydoc.on('update', onUpdate);

    return () => {
      ydoc.off('update', onUpdate);
      socket.close();
    };
  }, [displayName, docId, readOnly, token, ydoc]);

  return (
    <section className="editor-shell" aria-label="协同编辑区">
      <div className="editor-status">
        <span className={`status-dot ${status}`} />
        <span>{status === 'connected' ? '已连接' : status === 'connecting' ? '连接中' : '离线'}</span>
        <span className="presence">{online.length > 0 ? `在线：${online.join('、')}` : '等待协作者'}</span>
      </div>
      <EditorContent editor={editor} />
    </section>
  );
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function colorFor(value: string): string {
  const colors = ['#2563eb', '#0f766e', '#b45309', '#7c3aed', '#be123c'];
  const index = Array.from(value).reduce((sum, char) => sum + char.charCodeAt(0), 0) % colors.length;
  return colors[index];
}

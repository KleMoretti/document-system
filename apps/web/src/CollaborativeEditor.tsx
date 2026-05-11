import { useEffect, useMemo, useState } from 'react';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import Placeholder from '@tiptap/extension-placeholder';
import * as Y from 'yjs';
import { WS_BASE } from './config';

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
  const [online, setOnline] = useState<Record<string, number>>({});

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
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let cancelled = false;
    let attempts = 0;

    const scheduleReconnect = () => {
      if (cancelled || reconnectTimer !== null) {
        return;
      }
      setStatus('offline');
      const delay = Math.min(1000 * 2 ** attempts, 10000);
      attempts += 1;
      reconnectTimer = window.setTimeout(connect, delay);
    };

    function connect() {
      reconnectTimer = null;
      setStatus('connecting');
      socket = new WebSocket(`${WS_BASE}/ws/documents/${docId}?token=${encodeURIComponent(token)}`);

      socket.addEventListener('open', () => {
        attempts = 0;
        setStatus('connected');
        socket?.send(
          JSON.stringify({
            type: 'presence:update',
            displayName,
            color: colorFor(displayName)
          })
        );
      });

      socket.addEventListener('close', scheduleReconnect);
      socket.addEventListener('error', scheduleReconnect);

      socket.addEventListener('message', (event) => {
        const msg = JSON.parse(event.data as string) as SocketMessage;
        if (msg.type === 'sync:init') {
          msg.updates?.forEach((update) => Y.applyUpdate(ydoc, base64ToBytes(update), 'remote'));
        }
        if (msg.type === 'sync:update' && msg.update) {
          Y.applyUpdate(ydoc, base64ToBytes(msg.update), 'remote');
        }
        if (msg.type === 'presence:update' && msg.displayName) {
          setOnline((current) => ({ ...current, [msg.displayName!]: Date.now() }));
        }
        if (msg.type === 'error') {
          if (!shouldReconnectAfterSocketError(msg.code)) {
            cancelled = true;
          }
          socket?.close();
        }
      });
    }

    const onUpdate = (update: Uint8Array, origin: unknown) => {
      if (origin === 'remote' || socket?.readyState !== WebSocket.OPEN || readOnly) {
        return;
      }
      socket.send(JSON.stringify({ type: 'sync:update', update: bytesToBase64(update) }));
    };
    ydoc.on('update', onUpdate);
    connect();

    return () => {
      cancelled = true;
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
      }
      ydoc.off('update', onUpdate);
      socket?.close();
    };
  }, [displayName, docId, readOnly, token, ydoc]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const cutoff = Date.now() - 30_000;
      setOnline((current) =>
        Object.fromEntries(Object.entries(current).filter(([, lastSeen]) => lastSeen >= cutoff))
      );
    }, 5000);
    return () => window.clearInterval(timer);
  }, []);

  const onlineNames = Object.keys(online);

  function exportFile(kind: 'html' | 'md') {
    if (!editor) {
      return;
    }
    const content = kind === 'html' ? editor.getHTML() : editor.getText({ blockSeparator: '\n\n' });
    const blob = new Blob([content], { type: kind === 'html' ? 'text/html;charset=utf-8' : 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `document-${docId}.${kind}`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <section className="editor-shell" aria-label="协同编辑区">
      <div className="editor-status">
        <span className={`status-dot ${status}`} />
        <span>{status === 'connected' ? '已连接' : status === 'connecting' ? '连接中' : '离线'}</span>
        {readOnly && <span className="readonly-badge">只读</span>}
        <span className="presence">{onlineNames.length > 0 ? `在线：${onlineNames.join('、')}` : '等待协作者'}</span>
        <button type="button" onClick={() => exportFile('html')}>导出 HTML</button>
        <button type="button" onClick={() => exportFile('md')}>导出 Markdown</button>
      </div>
      <EditorContent editor={editor} />
    </section>
  );
}

export function shouldReconnectAfterSocketError(code?: string): boolean {
  return code !== 'UNAUTHORIZED' && code !== 'FORBIDDEN' && code !== 'INVALID_DOCUMENT_ID';
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

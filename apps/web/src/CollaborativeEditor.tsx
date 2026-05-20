import { useEffect, useMemo, useState } from 'react';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import Placeholder from '@tiptap/extension-placeholder';
import * as Y from 'yjs';
import { WS_BASE } from './config';
import { htmlToMarkdown, htmlToText, shouldApplyInitialImport } from './documentFormats';
import { buildStyledHtmlDocument, exportStyles } from './exportStyles';
import type { ExportFormat, ExportStyleId, PendingImport } from './types';

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
  documentTitle: string;
  token: string;
  readOnly: boolean;
  displayName: string;
  initialImport?: PendingImport;
  onInitialImportApplied?: (docId: string) => void;
};

export function CollaborativeEditor({
  docId,
  documentTitle,
  token,
  readOnly,
  displayName,
  initialImport,
  onInitialImportApplied
}: Props) {
  const ydoc = useMemo(() => new Y.Doc(), [docId]);
  const [status, setStatus] = useState<'connecting' | 'connected' | 'offline'>('connecting');
  const [online, setOnline] = useState<Record<string, number>>({});
  const [exportStyleId, setExportStyleId] = useState<ExportStyleId>('clean');

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
    if (!editor) {
      return;
    }

    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let cancelled = false;
    let attempts = 0;

    const scheduleReconnect = () => {
      if (cancelled || reconnectTimer !== null) {
        return;
      }
      setStatus('offline');
      const reconnect = nextReconnectDelay(attempts);
      if (!reconnect.shouldReconnect) {
        return;
      }
      attempts = reconnect.nextAttempts;
      const delay = reconnect.delay;
      reconnectTimer = window.setTimeout(connect, delay);
    };

    function connect() {
      reconnectTimer = null;
      setStatus('connecting');
      socket = new WebSocket(`${WS_BASE}/ws/documents/${docId}`, ['bearer', token]);

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
          if (shouldApplyInitialImport(initialImport, docId, msg.updates)) {
            editor.commands.setContent(initialImport!.html);
            onInitialImportApplied?.(docId);
          }
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
  }, [displayName, docId, editor, initialImport, onInitialImportApplied, readOnly, token, ydoc]);

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

  function exportFile(format: ExportFormat) {
    if (!editor) {
      return;
    }
    const html = editor.getHTML();
    if (format === 'pdf') {
      printHtml(buildStyledHtmlDocument(html, { title: documentTitle, styleId: exportStyleId }));
      return;
    }
    const content =
      format === 'html'
        ? buildStyledHtmlDocument(html, { title: documentTitle, styleId: exportStyleId })
        : format === 'markdown'
          ? htmlToMarkdown(html)
          : htmlToText(html);
    const extension = format === 'markdown' ? 'md' : format === 'text' ? 'txt' : 'html';
    const type =
      format === 'html'
        ? 'text/html;charset=utf-8'
        : format === 'markdown'
          ? 'text/markdown;charset=utf-8'
          : 'text/plain;charset=utf-8';
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `document-${docId}.${extension}`;
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
        <label className="export-style-picker">
          导出样式
          <select value={exportStyleId} onChange={(event) => setExportStyleId(event.target.value as ExportStyleId)}>
            {exportStyles.map((style) => (
              <option value={style.id} key={style.id}>
                {style.label}
              </option>
            ))}
          </select>
        </label>
        <button type="button" onClick={() => exportFile('html')}>导出 HTML</button>
        <button type="button" onClick={() => exportFile('markdown')}>导出 Markdown</button>
        <button type="button" onClick={() => exportFile('text')}>导出 TXT</button>
        <button type="button" onClick={() => exportFile('pdf')}>导出 PDF</button>
      </div>
      <EditorContent editor={editor} />
    </section>
  );
}

export function shouldReconnectAfterSocketError(code?: string): boolean {
  return code !== 'UNAUTHORIZED' && code !== 'FORBIDDEN' && code !== 'INVALID_DOCUMENT_ID';
}

export function nextReconnectDelay(attempts: number): { delay: number; nextAttempts: number; shouldReconnect: boolean } {
  const maxAttempts = 12;
  if (attempts >= maxAttempts) {
    return { delay: 0, nextAttempts: attempts, shouldReconnect: false };
  }
  return { delay: Math.min(1000 * 2 ** attempts, 10000), nextAttempts: attempts + 1, shouldReconnect: true };
}

function printHtml(htmlDocument: string) {
  const printWindow = window.open('', '_blank', 'noopener,noreferrer,width=900,height=700');
  if (!printWindow) {
    window.print();
    return;
  }
  printWindow.document.write(htmlDocument);
  printWindow.document.close();
  printWindow.focus();
  printWindow.print();
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

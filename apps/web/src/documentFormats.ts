import { marked } from 'marked';
import TurndownService from 'turndown';
import type { DocumentSummary, ImportFormat, PendingImport, PreparedImport } from './types';

const allowedTags = new Set([
  'a',
  'blockquote',
  'br',
  'code',
  'em',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'li',
  'ol',
  'p',
  'pre',
  's',
  'strong',
  'ul'
]);

export function detectImportFormat(fileName: string): ImportFormat {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.md') || lower.endsWith('.markdown')) {
    return 'markdown';
  }
  if (lower.endsWith('.html') || lower.endsWith('.htm')) {
    return 'html';
  }
  if (lower.endsWith('.txt')) {
    return 'text';
  }
  throw new Error('仅支持导入 Markdown、HTML 或 TXT 文件。');
}

export async function markdownToHtml(markdown: string): Promise<string> {
  return sanitizeImportedHtml(await marked.parse(markdown, { async: false }));
}

export function textToHtml(text: string): string {
  return text
    .split(/\n\s*\n/g)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`)
    .join('');
}

export function sanitizeImportedHtml(html: string): string {
  const document = new DOMParser().parseFromString(html, 'text/html');
  document.querySelectorAll('script, style, iframe, object, embed').forEach((node) => node.remove());

  Array.from(document.body.querySelectorAll('*')).forEach((element) => {
    const tagName = element.tagName.toLowerCase();
    if (!allowedTags.has(tagName)) {
      element.replaceWith(...Array.from(element.childNodes));
      return;
    }

    Array.from(element.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase();
      const value = attribute.value.trim().toLowerCase();
      const safeLinkAttribute = tagName === 'a' && (name === 'href' || name === 'title');
      if (!safeLinkAttribute || value.startsWith('javascript:') || value.startsWith('data:')) {
        element.removeAttribute(attribute.name);
      }
    });
  });

  return document.body.innerHTML.trim();
}

export function htmlToMarkdown(html: string): string {
  const service = new TurndownService({ bulletListMarker: '-', headingStyle: 'atx' });
  return service.turndown(sanitizeImportedHtml(html));
}

export function htmlToText(html: string): string {
  const document = new DOMParser().parseFromString(sanitizeImportedHtml(html), 'text/html');
  return document.body.textContent?.replace(/\n{3,}/g, '\n\n').trim() ?? '';
}

export async function buildPendingImport(file: File, docId: string): Promise<PendingImport> {
  return pendingImportFromPreparedImport(docId, await prepareImportFile(file));
}

export async function prepareImportFile(file: File): Promise<PreparedImport> {
  const format = detectImportFormat(file.name);
  const raw = await readFileText(file);
  const title = titleFromFileName(file.name);
  const html =
    format === 'markdown'
      ? await markdownToHtml(raw)
      : format === 'html'
        ? sanitizeImportedHtml(raw)
        : textToHtml(raw);
  return { title, format, html };
}

export function pendingImportFromPreparedImport(docId: string, preparedImport: PreparedImport): PendingImport {
  return { docId, ...preparedImport };
}

function readFileText(file: File): Promise<string> {
  if ('text' in file && typeof file.text === 'function') {
    return file.text();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(String(reader.result ?? '')));
    reader.addEventListener('error', () => reject(reader.error ?? new Error('读取文件失败。')));
    reader.readAsText(file);
  });
}

export async function importFileToNewDocument(
  file: File,
  createDocument: (title: string) => Promise<DocumentSummary>
): Promise<{ doc: DocumentSummary; pendingImport: PendingImport }> {
  const title = titleFromFileName(file.name);
  const doc = await createDocument(title);
  return { doc, pendingImport: await buildPendingImport(file, doc.id) };
}

export function shouldApplyInitialImport(
  pendingImport: PendingImport | undefined,
  docId: string,
  updates: string[] | undefined
): boolean {
  return Boolean(pendingImport && pendingImport.docId === docId && (updates?.length ?? 0) === 0);
}

function titleFromFileName(fileName: string): string {
  return fileName.replace(/\.[^.]+$/, '').trim() || '导入文档';
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

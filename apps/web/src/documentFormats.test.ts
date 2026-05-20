import { describe, expect, it, vi } from 'vitest';
import {
  buildPendingImport,
  detectImportFormat,
  htmlToMarkdown,
  htmlToText,
  importFileToNewDocument,
  markdownToHtml,
  prepareImportFile,
  sanitizeImportedHtml,
  shouldApplyInitialImport,
  textToHtml
} from './documentFormats';

describe('document format conversion', () => {
  it('detects supported import formats from file names', () => {
    expect(detectImportFormat('notes.md')).toBe('markdown');
    expect(detectImportFormat('notes.markdown')).toBe('markdown');
    expect(detectImportFormat('page.html')).toBe('html');
    expect(detectImportFormat('draft.txt')).toBe('text');
  });

  it('converts markdown into safe html', async () => {
    const html = await markdownToHtml('# 标题\n\n- 第一项\n- 第二项\n\n<script>alert(1)</script>');

    expect(html).toContain('<h1>标题</h1>');
    expect(html).toContain('<li>第一项</li>');
    expect(html).not.toContain('<script>');
  });

  it('sanitizes imported html', () => {
    const html = sanitizeImportedHtml('<h1 onclick="evil()">标题</h1><a href="javascript:alert(1)">link</a><script>bad()</script>');

    expect(html).toContain('<h1>标题</h1>');
    expect(html).toContain('<a>link</a>');
    expect(html).not.toContain('onclick');
    expect(html).not.toContain('javascript:');
    expect(html).not.toContain('<script>');
  });

  it('converts text into escaped paragraph html', () => {
    expect(textToHtml('第一段 <x>\n\n第二段')).toBe('<p>第一段 &lt;x&gt;</p><p>第二段</p>');
  });

  it('exports html to markdown and text', () => {
    const html = '<h1>标题</h1><p>正文</p><ul><li>第一项</li></ul>';

    expect(htmlToMarkdown(html)).toContain('# 标题');
    expect(htmlToMarkdown(html)).toContain('-   第一项');
    expect(htmlToText(html)).toContain('标题');
    expect(htmlToText(html)).toContain('正文');
  });

  it('builds pending imports with derived titles', async () => {
    const file = new File(['# Roadmap'], 'roadmap.md', { type: 'text/markdown' });

    await expect(buildPendingImport(file, 'doc-1')).resolves.toMatchObject({
      docId: 'doc-1',
      title: 'roadmap',
      format: 'markdown'
    });
  });

  it('prepares imports for preview before creating documents', async () => {
    const file = new File(['# Preview'], 'preview.md', { type: 'text/markdown' });

    await expect(prepareImportFile(file)).resolves.toMatchObject({
      title: 'preview',
      format: 'markdown',
      html: '<h1>Preview</h1>'
    });
  });

  it('imports a file by creating a new document first', async () => {
    const file = new File(['plain text'], 'memo.txt', { type: 'text/plain' });
    const createDocument = vi.fn().mockResolvedValue({ id: 'doc-2', title: 'memo' });

    const result = await importFileToNewDocument(file, createDocument);

    expect(createDocument).toHaveBeenCalledWith('memo');
    expect(result.doc.id).toBe('doc-2');
    expect(result.pendingImport.html).toBe('<p>plain text</p>');
  });

  it('applies initial import only to the matching empty document', () => {
    const pendingImport = { docId: 'doc-1', title: 'Doc', format: 'html' as const, html: '<p>Hello</p>' };

    expect(shouldApplyInitialImport(pendingImport, 'doc-1', [])).toBe(true);
    expect(shouldApplyInitialImport(pendingImport, 'doc-2', [])).toBe(false);
    expect(shouldApplyInitialImport(pendingImport, 'doc-1', ['base64-update'])).toBe(false);
    expect(shouldApplyInitialImport(undefined, 'doc-1', [])).toBe(false);
  });
});

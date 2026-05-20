import { sanitizeImportedHtml } from './documentFormats';
import type { ExportStyleId } from './types';

export type ExportStyle = {
  id: ExportStyleId;
  label: string;
  description: string;
  css: string;
};

export const exportStyles: ExportStyle[] = [
  {
    id: 'clean',
    label: '清爽',
    description: '适合普通文档和知识库归档。',
    css: `
      body { margin: 0; background: #f8fafc; color: #172033; font-family: Georgia, 'Times New Roman', serif; }
      .export-document { max-width: 820px; margin: 0 auto; padding: 48px; background: #ffffff; line-height: 1.75; }
      h1, h2, h3 { color: #0f172a; line-height: 1.25; }
      p, li { font-size: 16px; }
    `
  },
  {
    id: 'report',
    label: '报告',
    description: '标题更醒目，适合正式汇报或 PDF。',
    css: `
      body { margin: 0; background: #e8edf5; color: #162033; font-family: 'Times New Roman', Georgia, serif; }
      .export-document { max-width: 860px; margin: 32px auto; padding: 56px; background: #ffffff; border-top: 10px solid #1d4ed8; line-height: 1.78; }
      h1 { font-size: 34px; letter-spacing: -0.02em; }
      h2 { margin-top: 32px; border-bottom: 1px solid #d8e0eb; padding-bottom: 6px; }
      p, li { font-size: 16px; }
    `
  },
  {
    id: 'compact',
    label: '紧凑',
    description: '减少留白，适合较长文档导出。',
    css: `
      body { margin: 0; background: #ffffff; color: #111827; font-family: Cambria, Georgia, serif; }
      .export-document { max-width: 900px; margin: 0 auto; padding: 28px 36px; line-height: 1.55; }
      h1, h2, h3, p, ul, ol { margin-top: 0.7em; margin-bottom: 0.45em; }
      p, li { font-size: 14px; }
    `
  }
];

export function getExportStyle(id: string): ExportStyle {
  return exportStyles.find((style) => style.id === id) ?? exportStyles[0];
}

export function buildStyledHtmlDocument(
  html: string,
  options: { title: string; styleId: string }
): string {
  const style = getExportStyle(options.styleId);
  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${escapeHtml(options.title || '导出文档')}</title>
    <style>${style.css}</style>
  </head>
  <body data-export-style="${style.id}">
    <main class="export-document">${sanitizeImportedHtml(html)}</main>
  </body>
</html>`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

import { describe, expect, it } from 'vitest';
import { buildStyledHtmlDocument, exportStyles, getExportStyle } from './exportStyles';

describe('export styles', () => {
  it('offers clean, report, and compact export styles', () => {
    expect(exportStyles.map((style) => style.id)).toEqual(['clean', 'report', 'compact']);
  });

  it('wraps editor html in a full styled html document', () => {
    const output = buildStyledHtmlDocument('<h1>标题</h1><p>正文</p>', {
      title: '产品计划',
      styleId: 'report'
    });

    expect(output).toContain('<!doctype html>');
    expect(output).toContain('<title>产品计划</title>');
    expect(output).toContain('data-export-style="report"');
    expect(output).toContain('<main class="export-document">');
    expect(output).toContain('<h1>标题</h1><p>正文</p>');
  });

  it('falls back to the clean style for unknown style ids', () => {
    expect(getExportStyle('missing')).toEqual(getExportStyle('clean'));
  });
});

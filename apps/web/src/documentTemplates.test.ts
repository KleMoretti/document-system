import { describe, expect, it } from 'vitest';
import { documentTemplates, getDocumentTemplate } from './documentTemplates';

describe('document templates', () => {
  it('exposes practical templates for new documents', () => {
    expect(documentTemplates.map((template) => template.id)).toEqual([
      'blank',
      'meeting-notes',
      'project-plan',
      'weekly-report'
    ]);
  });

  it('returns safe html and suggested titles for templates', () => {
    const template = getDocumentTemplate('meeting-notes');

    expect(template.title).toBe('会议纪要');
    expect(template.html).toContain('<h1>会议纪要</h1>');
    expect(template.html).toContain('<ul>');
    expect(template.html).not.toContain('<script>');
  });
});

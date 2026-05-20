import type { DocumentTemplateId, PreparedImport } from './types';

export type DocumentTemplate = PreparedImport & {
  id: DocumentTemplateId;
  description: string;
};

export const documentTemplates: DocumentTemplate[] = [
  {
    id: 'blank',
    title: '空白文档',
    description: '从一个干净页面开始。',
    format: 'html',
    html: '<p></p>'
  },
  {
    id: 'meeting-notes',
    title: '会议纪要',
    description: '记录议题、结论、行动项和负责人。',
    format: 'html',
    html: [
      '<h1>会议纪要</h1>',
      '<p><strong>会议时间：</strong></p>',
      '<p><strong>参会人员：</strong></p>',
      '<h2>议题</h2>',
      '<ul><li>议题一</li><li>议题二</li></ul>',
      '<h2>结论</h2>',
      '<p></p>',
      '<h2>行动项</h2>',
      '<ul><li>负责人 - 截止时间 - 事项</li></ul>'
    ].join('')
  },
  {
    id: 'project-plan',
    title: '项目计划',
    description: '梳理目标、范围、里程碑、风险和交付物。',
    format: 'html',
    html: [
      '<h1>项目计划</h1>',
      '<h2>目标</h2>',
      '<p>说明项目要达成的业务结果。</p>',
      '<h2>范围</h2>',
      '<ul><li>包含内容</li><li>不包含内容</li></ul>',
      '<h2>里程碑</h2>',
      '<ul><li>阶段 - 时间 - 交付物</li></ul>',
      '<h2>风险</h2>',
      '<p>列出主要风险和应对方案。</p>'
    ].join('')
  },
  {
    id: 'weekly-report',
    title: '周报',
    description: '沉淀本周进展、问题和下周计划。',
    format: 'html',
    html: [
      '<h1>周报</h1>',
      '<h2>本周完成</h2>',
      '<ul><li>完成事项</li></ul>',
      '<h2>遇到的问题</h2>',
      '<ul><li>问题与影响</li></ul>',
      '<h2>下周计划</h2>',
      '<ul><li>计划事项</li></ul>'
    ].join('')
  }
];

export function getDocumentTemplate(id: string): DocumentTemplate {
  return documentTemplates.find((template) => template.id === id) ?? documentTemplates[0];
}

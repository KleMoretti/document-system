export type User = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
};

export type DocumentSummary = {
  id: string;
  title: string;
  ownerId: string;
  role: 'owner' | 'editor' | 'viewer';
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
};

export type Share = {
  userId: string;
  email: string;
  displayName: string;
  role: 'owner' | 'editor' | 'viewer';
};

export type AuthResponse = {
  token: string;
  user: User;
};

export type ApiError = {
  code: string;
  message: string;
};

export type ImportFormat = 'markdown' | 'html' | 'text';

export type ExportFormat = 'markdown' | 'html' | 'text' | 'pdf';

export type ExportStyleId = 'clean' | 'report' | 'compact';

export type DocumentTemplateId = 'blank' | 'meeting-notes' | 'project-plan' | 'weekly-report';

export type PreparedImport = {
  title: string;
  format: ImportFormat;
  html: string;
};

export type PendingImport = {
  docId: string;
  title: string;
  format: ImportFormat;
  html: string;
};

export type DocumentStatus = 'active' | 'deleted';

export type DocumentVersionSummary = {
  id: string;
  documentId: string;
  label: string;
  createdBy: string;
  createdAt: string;
};

export type DocumentVersion = DocumentVersionSummary & {
  updates: string[];
};

export type CommentReply = {
  id: string;
  commentId: string;
  authorId: string;
  authorName: string;
  body: string;
  createdAt: string;
};

export type CommentThread = {
  id: string;
  documentId: string;
  authorId: string;
  authorName: string;
  body: string;
  resolved: boolean;
  createdAt: string;
  updatedAt: string;
  replies: CommentReply[];
};

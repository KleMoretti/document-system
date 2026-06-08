import type {
  AuthResponse,
  CommentThread,
  DocumentStatus,
  DocumentSummary,
  DocumentVersion,
  DocumentVersionSummary,
  Share,
  User
} from './types';
import { API_BASE, AUTH_API_BASE } from './config';

export function joinUrl(base: string, path: string): string {
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
}

export function tokenHeader(token: string | null): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const base = path.startsWith('/api/auth') || path === '/api/me' ? AUTH_API_BASE : API_BASE;
  const response = await fetch(joinUrl(base, path), {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {})
    }
  });

  if (!response.ok) {
    let message = `Request failed with ${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // Keep the status-derived message.
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function documentsPath(filters?: { query?: string; status?: DocumentStatus }): string {
  const params = new URLSearchParams();
  if (filters?.query?.trim()) {
    params.set('query', filters.query.trim());
  }
  if (filters?.status) {
    params.set('status', filters.status);
  }
  const query = params.toString();
  return query ? `/api/documents?${query}` : '/api/documents';
}

export const api = {
  register(input: { email: string; password: string; displayName: string }) {
    return request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(input)
    });
  },
  login(input: { email: string; password: string }) {
    return request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(input)
    });
  },
  me(token: string) {
    return request<User>('/api/me', { headers: tokenHeader(token) });
  },
  listDocuments(token: string, filters?: { query?: string; status?: DocumentStatus }) {
    return request<DocumentSummary[]>(documentsPath(filters), { headers: tokenHeader(token) });
  },
  createDocument(token: string, title: string) {
    return request<DocumentSummary>('/api/documents', {
      method: 'POST',
      headers: tokenHeader(token),
      body: JSON.stringify({ title })
    });
  },
  renameDocument(token: string, docId: string, title: string) {
    return request<DocumentSummary>(`/api/documents/${docId}`, {
      method: 'PATCH',
      headers: tokenHeader(token),
      body: JSON.stringify({ title })
    });
  },
  deleteDocument(token: string, docId: string) {
    return request<void>(`/api/documents/${docId}`, {
      method: 'DELETE',
      headers: tokenHeader(token)
    });
  },
  listShares(token: string, docId: string) {
    return request<Share[]>(`/api/documents/${docId}/shares`, { headers: tokenHeader(token) });
  },
  shareDocument(token: string, docId: string, email: string, role: 'editor' | 'viewer') {
    return request<void>(`/api/documents/${docId}/shares`, {
      method: 'POST',
      headers: tokenHeader(token),
      body: JSON.stringify({ email, role })
    });
  },
  removeShare(token: string, docId: string, userId: string) {
    return request<void>(`/api/documents/${docId}/shares/${userId}`, {
      method: 'DELETE',
      headers: tokenHeader(token)
    });
  },
  restoreDocument(token: string, docId: string) {
    return request<DocumentSummary>(`/api/documents/${docId}/restore`, {
      method: 'POST',
      headers: tokenHeader(token)
    });
  },
  listVersions(token: string, docId: string) {
    return request<DocumentVersionSummary[]>(`/api/documents/${docId}/versions`, {
      headers: tokenHeader(token)
    });
  },
  createVersion(token: string, docId: string, label: string) {
    return request<DocumentVersionSummary>(`/api/documents/${docId}/versions`, {
      method: 'POST',
      headers: tokenHeader(token),
      body: JSON.stringify({ label })
    });
  },
  getVersion(token: string, docId: string, versionId: string) {
    return request<DocumentVersion>(`/api/documents/${docId}/versions/${versionId}`, {
      headers: tokenHeader(token)
    });
  },
  restoreVersion(token: string, docId: string, versionId: string) {
    return request<void>(`/api/documents/${docId}/versions/${versionId}/restore`, {
      method: 'POST',
      headers: tokenHeader(token)
    });
  },
  listComments(token: string, docId: string) {
    return request<CommentThread[]>(`/api/documents/${docId}/comments`, {
      headers: tokenHeader(token)
    });
  },
  createComment(token: string, docId: string, body: string) {
    return request<CommentThread>(`/api/documents/${docId}/comments`, {
      method: 'POST',
      headers: tokenHeader(token),
      body: JSON.stringify({ body })
    });
  },
  replyToComment(token: string, docId: string, commentId: string, body: string) {
    return request<CommentThread>(`/api/documents/${docId}/comments/${commentId}/replies`, {
      method: 'POST',
      headers: tokenHeader(token),
      body: JSON.stringify({ body })
    });
  },
  updateComment(token: string, docId: string, commentId: string, input: { body?: string; resolved?: boolean }) {
    return request<CommentThread>(`/api/documents/${docId}/comments/${commentId}`, {
      method: 'PATCH',
      headers: tokenHeader(token),
      body: JSON.stringify(input)
    });
  }
};

import type { AuthResponse, DocumentSummary, Share, User } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export function joinUrl(base: string, path: string): string {
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
}

export function tokenHeader(token: string | null): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(joinUrl(API_BASE, path), {
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
  listDocuments(token: string) {
    return request<DocumentSummary[]>('/api/documents', { headers: tokenHeader(token) });
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
  }
};

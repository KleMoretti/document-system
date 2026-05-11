import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, joinUrl, tokenHeader } from './api';

describe('api utilities', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('joins base urls without duplicate slashes', () => {
    expect(joinUrl('http://localhost:8080/', '/api/me')).toBe('http://localhost:8080/api/me');
    expect(joinUrl('http://localhost:8080', 'api/me')).toBe('http://localhost:8080/api/me');
  });

  it('builds bearer headers when token exists', () => {
    expect(tokenHeader('abc')).toEqual({ Authorization: 'Bearer abc' });
    expect(tokenHeader(null)).toEqual({});
  });

  it('lists documents with optional search and status filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => []
    });
    vi.stubGlobal('fetch', fetchMock);

    await api.listDocuments('token-1', { query: 'roadmap', status: 'deleted' });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/documents?query=roadmap&status=deleted',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer token-1' })
      })
    );
  });

  it('creates document versions with labels', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'version-1', label: 'Milestone' })
    });
    vi.stubGlobal('fetch', fetchMock);

    await api.createVersion('token-1', 'doc-1', 'Milestone');

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/documents/doc-1/versions',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ label: 'Milestone' })
      })
    );
  });
});

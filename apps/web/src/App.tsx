import { FormEvent, useEffect, useMemo, useState } from 'react';
import { FileText, LogOut, Plus, RefreshCcw, Share2, Trash2 } from 'lucide-react';
import { api } from './api';
import { CollaborativeEditor } from './CollaborativeEditor';
import type { DocumentSummary, Share, User } from './types';

type AuthMode = 'login' | 'register';

export function App() {
  const [token, setToken] = useState(() => localStorage.getItem('doc-token'));
  const [user, setUser] = useState<User | null>(null);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [selected, setSelected] = useState<DocumentSummary | null>(null);
  const [mode, setMode] = useState<AuthMode>('login');
  const [email, setEmail] = useState('ada@example.com');
  const [password, setPassword] = useState('password123');
  const [displayName, setDisplayName] = useState('Ada');
  const [newTitle, setNewTitle] = useState('新文档');
  const [error, setError] = useState<string | null>(null);
  const [shares, setShares] = useState<Share[]>([]);
  const [shareEmail, setShareEmail] = useState('');
  const [shareRole, setShareRole] = useState<'editor' | 'viewer'>('editor');

  const apiBase = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

  const selectedRole = selected?.role ?? 'viewer';
  const canEdit = selectedRole === 'owner' || selectedRole === 'editor';
  const canShare = selectedRole === 'owner';

  useEffect(() => {
    if (!token) {
      setUser(null);
      return;
    }
    api
      .me(token)
      .then(setUser)
      .catch(() => logout());
  }, [token]);

  useEffect(() => {
    if (token) {
      void loadDocuments(token);
    }
  }, [token]);

  useEffect(() => {
    if (token && selected && canShare) {
      api.listShares(token, selected.id).then(setShares).catch(() => setShares([]));
    } else {
      setShares([]);
    }
  }, [canShare, selected, token]);

  const sortedDocuments = useMemo(
    () => [...documents].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
    [documents]
  );

  async function submitAuth(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const response =
        mode === 'login'
          ? await api.login({ email, password })
          : await api.register({ email, password, displayName });
      localStorage.setItem('doc-token', response.token);
      setToken(response.token);
      setUser(response.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : '认证失败');
    }
  }

  async function loadDocuments(currentToken = token) {
    if (!currentToken) {
      return;
    }
    const items = await api.listDocuments(currentToken);
    setDocuments(items);
    setSelected((current) => current ?? items[0] ?? null);
  }

  async function createDocument(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    const doc = await api.createDocument(token, newTitle);
    setDocuments((current) => [doc, ...current]);
    setSelected(doc);
    setNewTitle('新文档');
  }

  async function deleteSelected() {
    if (!token || !selected) {
      return;
    }
    await api.deleteDocument(token, selected.id);
    const next = documents.filter((doc) => doc.id !== selected.id);
    setDocuments(next);
    setSelected(next[0] ?? null);
  }

  async function shareSelected(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected) {
      return;
    }
    await api.shareDocument(token, selected.id, shareEmail, shareRole);
    setShareEmail('');
    setShares(await api.listShares(token, selected.id));
  }

  async function removeShare(userId: string) {
    if (!token || !selected) {
      return;
    }
    await api.removeShare(token, selected.id, userId);
    setShares(await api.listShares(token, selected.id));
  }

  function logout() {
    localStorage.removeItem('doc-token');
    setToken(null);
    setUser(null);
    setDocuments([]);
    setSelected(null);
  }

  if (!token || !user) {
    return (
      <main className="auth-layout">
        <form className="auth-panel" onSubmit={submitAuth}>
          <div>
            <p className="eyebrow">Documentation Collab</p>
            <h1>在线文档协同编辑</h1>
            <p className="muted">一套前端，可连接 Java 或 Go 后端。</p>
          </div>
          <label>
            邮箱
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" />
          </label>
          {mode === 'register' && (
            <label>
              昵称
              <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} autoComplete="name" />
            </label>
          )}
          <label>
            密码
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button className="primary" type="submit">
            {mode === 'login' ? '登录' : '注册'}
          </button>
          <button className="text-button" type="button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
            {mode === 'login' ? '创建账号' : '已有账号，去登录'}
          </button>
          <p className="backend-line">当前 API：{apiBase}</p>
        </form>
      </main>
    );
  }

  return (
    <main className="app-layout">
      <aside className="sidebar">
        <header className="sidebar-header">
          <div>
            <p className="eyebrow">Workspace</p>
            <h1>文档</h1>
          </div>
          <button className="icon-button" onClick={logout} aria-label="退出登录" title="退出登录">
            <LogOut size={18} />
          </button>
        </header>

        <form className="create-row" onSubmit={createDocument}>
          <input value={newTitle} onChange={(event) => setNewTitle(event.target.value)} aria-label="新文档标题" />
          <button className="icon-button primary-icon" type="submit" aria-label="新建文档" title="新建文档">
            <Plus size={18} />
          </button>
        </form>

        <div className="toolbar-line">
          <span>{user.displayName}</span>
          <button className="icon-button" onClick={() => void loadDocuments()} aria-label="刷新文档" title="刷新文档">
            <RefreshCcw size={16} />
          </button>
        </div>

        <nav className="document-list" aria-label="文档列表">
          {sortedDocuments.map((doc) => (
            <button
              key={doc.id}
              className={`document-item ${selected?.id === doc.id ? 'active' : ''}`}
              onClick={() => setSelected(doc)}
              type="button"
            >
              <FileText size={18} />
              <span>{doc.title}</span>
              <small>{doc.role}</small>
            </button>
          ))}
          {sortedDocuments.length === 0 && <p className="empty">暂无文档</p>}
        </nav>
      </aside>

      <section className="main-panel">
        {selected ? (
          <>
            <header className="document-header">
              <div>
                <p className="eyebrow">{selected.role}</p>
                <h2>{selected.title}</h2>
              </div>
              <div className="header-actions">
                {canShare && (
                  <span className="share-label">
                    <Share2 size={16} />
                    可分享
                  </span>
                )}
                {selected.role === 'owner' && (
                  <button className="icon-button danger" onClick={() => void deleteSelected()} aria-label="删除文档" title="删除文档">
                    <Trash2 size={18} />
                  </button>
                )}
              </div>
            </header>
            <div className="work-surface">
              <CollaborativeEditor
                key={selected.id}
                docId={selected.id}
                token={token}
                readOnly={!canEdit}
                displayName={user.displayName}
              />
              {canShare && (
                <aside className="share-panel" aria-label="分享权限">
                  <h3>分享</h3>
                  <form onSubmit={shareSelected}>
                    <label>
                      用户邮箱
                      <input value={shareEmail} onChange={(event) => setShareEmail(event.target.value)} type="email" />
                    </label>
                    <label>
                      权限
                      <select value={shareRole} onChange={(event) => setShareRole(event.target.value as 'editor' | 'viewer')}>
                        <option value="editor">editor</option>
                        <option value="viewer">viewer</option>
                      </select>
                    </label>
                    <button className="primary" type="submit">
                      添加
                    </button>
                  </form>
                  <div className="share-list">
                    {shares.map((share) => (
                      <div className="share-item" key={share.userId}>
                        <span>{share.displayName || share.email}</span>
                        <small>{share.role}</small>
                        {share.role !== 'owner' && (
                          <button type="button" onClick={() => void removeShare(share.userId)}>
                            移除
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </aside>
              )}
            </div>
          </>
        ) : (
          <div className="empty-state">
            <FileText size={42} />
            <h2>选择或创建文档</h2>
          </div>
        )}
      </section>
    </main>
  );
}

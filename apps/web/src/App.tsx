import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { ArchiveRestore, FileText, FileUp, LayoutTemplate, LogOut, Plus, RefreshCcw, Share2, Trash2, X } from 'lucide-react';
import { api } from './api';
import { CollaborativeEditor } from './CollaborativeEditor';
import { API_BASE } from './config';
import { documentTemplates, getDocumentTemplate } from './documentTemplates';
import { pendingImportFromPreparedImport, prepareImportFile } from './documentFormats';
import type {
  CommentThread,
  DocumentTemplateId,
  DocumentStatus,
  DocumentSummary,
  DocumentVersionSummary,
  PendingImport,
  PreparedImport,
  Share,
  User
} from './types';

type AuthMode = 'login' | 'register';

export function App() {
  const [token, setToken] = useState(() => localStorage.getItem('doc-token'));
  const [user, setUser] = useState<User | null>(null);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [selected, setSelected] = useState<DocumentSummary | null>(null);
  const [mode, setMode] = useState<AuthMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [newTitle, setNewTitle] = useState('新文档');
  const [templateId, setTemplateId] = useState<DocumentTemplateId>('blank');
  const [renameTitle, setRenameTitle] = useState('');
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<DocumentStatus>('active');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shares, setShares] = useState<Share[]>([]);
  const [shareEmail, setShareEmail] = useState('');
  const [shareRole, setShareRole] = useState<'editor' | 'viewer'>('editor');
  const [versions, setVersions] = useState<DocumentVersionSummary[]>([]);
  const [versionLabel, setVersionLabel] = useState('');
  const [comments, setComments] = useState<CommentThread[]>([]);
  const [commentBody, setCommentBody] = useState('');
  const [editorRevision, setEditorRevision] = useState(0);
  const [pendingImport, setPendingImport] = useState<PendingImport | undefined>();
  const [preparedImport, setPreparedImport] = useState<PreparedImport | undefined>();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const selectedRole = selected?.role ?? 'viewer';
  const isDeleted = Boolean(selected?.deletedAt);
  const canEdit = !isDeleted && (selectedRole === 'owner' || selectedRole === 'editor');
  const canShare = !isDeleted && selectedRole === 'owner';

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
  }, [query, statusFilter, token]);

  useEffect(() => {
    setRenameTitle(selected?.title ?? '');
  }, [selected?.id, selected?.title]);

  useEffect(() => {
    if (!token || !selected || isDeleted) {
      setShares([]);
      setVersions([]);
      setComments([]);
      return;
    }
    void loadDocumentSidebars(token, selected.id);
  }, [canShare, isDeleted, selected?.id, token]);

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
    setLoading(true);
    setError(null);
    try {
      const items = await api.listDocuments(currentToken, { query, status: statusFilter });
      setDocuments(items);
      setSelected((current) => {
        if (current && items.some((item) => item.id === current.id)) {
          return items.find((item) => item.id === current.id) ?? current;
        }
        return items[0] ?? null;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载文档失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadDocumentSidebars(currentToken: string, docId: string) {
    const [shareItems, versionItems, commentItems] = await Promise.all([
      canShare ? api.listShares(currentToken, docId).catch(() => []) : Promise.resolve([]),
      api.listVersions(currentToken, docId).catch(() => []),
      api.listComments(currentToken, docId).catch(() => [])
    ]);
    setShares(shareItems);
    setVersions(versionItems);
    setComments(commentItems);
  }

  async function createDocument(event: FormEvent) {
    event.preventDefault();
    if (!token || !newTitle.trim()) {
      return;
    }
    const doc = await api.createDocument(token, newTitle.trim());
    setDocuments((current) => [doc, ...current]);
    setSelected(doc);
    setNewTitle('新文档');
  }

  async function createDocumentFromTemplate() {
    if (!token) {
      return;
    }
    const template = getDocumentTemplate(templateId);
    const doc = await api.createDocument(token, template.title);
    setDocuments((current) => [doc, ...current]);
    setSelected(doc);
    setPendingImport(pendingImportFromPreparedImport(doc.id, template));
    setStatusFilter('active');
  }

  async function importDocument(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setError(null);
    try {
      setPreparedImport(await prepareImportFile(file));
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败');
    } finally {
      event.target.value = '';
    }
  }

  async function confirmPreparedImport() {
    if (!token || !preparedImport) {
      return;
    }
    const doc = await api.createDocument(token, preparedImport.title);
    setDocuments((current) => [doc, ...current]);
    setSelected(doc);
    setPendingImport(pendingImportFromPreparedImport(doc.id, preparedImport));
    setPreparedImport(undefined);
    setStatusFilter('active');
  }

  async function renameSelected(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected || !renameTitle.trim()) {
      return;
    }
    const updated = await api.renameDocument(token, selected.id, renameTitle.trim());
    setSelected(updated);
    setDocuments((current) => current.map((doc) => (doc.id === updated.id ? updated : doc)));
  }

  async function deleteSelected() {
    if (!token || !selected || !window.confirm(`删除文档“${selected.title}”？可以在回收站恢复。`)) {
      return;
    }
    await api.deleteDocument(token, selected.id);
    const next = documents.filter((doc) => doc.id !== selected.id);
    setDocuments(next);
    setSelected(next[0] ?? null);
  }

  async function restoreSelected() {
    if (!token || !selected) {
      return;
    }
    const restored = await api.restoreDocument(token, selected.id);
    setDocuments((current) => current.filter((doc) => doc.id !== selected.id));
    setSelected(statusFilter === 'active' ? restored : null);
  }

  async function shareSelected(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected || !shareEmail.trim()) {
      return;
    }
    await api.shareDocument(token, selected.id, shareEmail.trim(), shareRole);
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

  async function createVersion(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected) {
      return;
    }
    await api.createVersion(token, selected.id, versionLabel.trim());
    setVersionLabel('');
    setVersions(await api.listVersions(token, selected.id));
  }

  async function restoreVersion(versionId: string) {
    if (!token || !selected || !window.confirm('恢复到该版本？当前未保存的实时编辑状态需要重新打开文档后同步。')) {
      return;
    }
    await api.restoreVersion(token, selected.id, versionId);
    setEditorRevision((value) => value + 1);
    await loadDocuments();
  }

  async function createComment(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected || !commentBody.trim()) {
      return;
    }
    await api.createComment(token, selected.id, commentBody.trim());
    setCommentBody('');
    setComments(await api.listComments(token, selected.id));
  }

  async function toggleComment(comment: CommentThread) {
    if (!token || !selected) {
      return;
    }
    await api.updateComment(token, selected.id, comment.id, { resolved: !comment.resolved });
    setComments(await api.listComments(token, selected.id));
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
          <p className="backend-line">当前 API：{API_BASE}</p>
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

        <div className="template-picker" aria-label="文档模板">
          <label>
            文档模板
            <select value={templateId} onChange={(event) => setTemplateId(event.target.value as DocumentTemplateId)}>
              {documentTemplates.map((template) => (
                <option value={template.id} key={template.id}>
                  {template.title}
                </option>
              ))}
            </select>
          </label>
          <p className="muted">{getDocumentTemplate(templateId).description}</p>
          <button className="import-button" type="button" onClick={() => void createDocumentFromTemplate()}>
            <LayoutTemplate size={16} />
            从模板创建
          </button>
        </div>

        <input
          ref={fileInputRef}
          className="visually-hidden"
          type="file"
          accept=".md,.markdown,.html,.htm,.txt"
          onChange={importDocument}
        />
        <button className="import-button" type="button" onClick={() => fileInputRef.current?.click()}>
          <FileUp size={16} />
          导入 Markdown / HTML / TXT
        </button>

        <div className="filter-stack">
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索标题" aria-label="搜索标题" />
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as DocumentStatus)} aria-label="文档状态">
            <option value="active">有效文档</option>
            <option value="deleted">回收站</option>
          </select>
        </div>

        <div className="toolbar-line">
          <span>{user.displayName}</span>
          <button className="icon-button" onClick={() => void loadDocuments()} aria-label="刷新文档" title="刷新文档">
            <RefreshCcw size={16} />
          </button>
        </div>
        {error && <p className="error">{error}</p>}

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
          {sortedDocuments.length === 0 && <p className="empty">{loading ? '加载中...' : '暂无文档'}</p>}
        </nav>
      </aside>

      <section className="main-panel">
        {selected ? (
          <>
            <header className="document-header">
              <div>
                <p className="eyebrow">{isDeleted ? 'deleted' : selected.role}</p>
                <h2>{selected.title}</h2>
              </div>
              <div className="header-actions">
                {canShare && (
                  <span className="share-label">
                    <Share2 size={16} />
                    可分享
                  </span>
                )}
                {isDeleted && selected.role === 'owner' && (
                  <button className="icon-button" onClick={() => void restoreSelected()} aria-label="恢复文档" title="恢复文档">
                    <ArchiveRestore size={18} />
                  </button>
                )}
                {!isDeleted && selected.role === 'owner' && (
                  <button className="icon-button danger" onClick={() => void deleteSelected()} aria-label="删除文档" title="删除文档">
                    <Trash2 size={18} />
                  </button>
                )}
              </div>
            </header>
            <div className="work-surface">
              <div className="document-column">
                {canEdit && (
                  <form className="rename-row" onSubmit={renameSelected}>
                    <input value={renameTitle} onChange={(event) => setRenameTitle(event.target.value)} aria-label="文档标题" />
                    <button className="primary" type="submit">重命名</button>
                  </form>
                )}
                {isDeleted ? (
                  <div className="empty-state">
                    <ArchiveRestore size={42} />
                    <h2>文档在回收站中</h2>
                    <p className="muted">恢复后才能继续编辑、评论或保存版本。</p>
                  </div>
                ) : (
                  <CollaborativeEditor
                    key={`${selected.id}-${editorRevision}`}
                    docId={selected.id}
                    documentTitle={selected.title}
                    token={token}
                    readOnly={!canEdit}
                    displayName={user.displayName}
                    initialImport={pendingImport?.docId === selected.id ? pendingImport : undefined}
                    onInitialImportApplied={(docId) => {
                      setPendingImport((current) => (current?.docId === docId ? undefined : current));
                    }}
                  />
                )}
              </div>

              {!isDeleted && (
                <aside className="side-panels" aria-label="协作工具">
                  {canShare && (
                    <section className="share-panel" aria-label="分享权限">
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
                        <button className="primary" type="submit">添加</button>
                      </form>
                      <div className="share-list">
                        {shares.map((share) => (
                          <div className="share-item" key={share.userId}>
                            <span>{share.displayName || share.email}</span>
                            <small>{share.role}</small>
                            {share.role !== 'owner' && (
                              <button type="button" onClick={() => void removeShare(share.userId)}>移除</button>
                            )}
                          </div>
                        ))}
                      </div>
                    </section>
                  )}

                  <section className="share-panel" aria-label="历史版本">
                    <h3>历史版本</h3>
                    <form onSubmit={createVersion}>
                      <input value={versionLabel} onChange={(event) => setVersionLabel(event.target.value)} placeholder="版本标签" />
                      <button className="primary" type="submit">保存版本</button>
                    </form>
                    <div className="share-list">
                      {versions.map((version) => (
                        <div className="version-item" key={version.id}>
                          <span>{version.label}</span>
                          <small>{new Date(version.createdAt).toLocaleString()}</small>
                          {canEdit && <button type="button" onClick={() => void restoreVersion(version.id)}>恢复</button>}
                        </div>
                      ))}
                      {versions.length === 0 && <p className="empty">暂无版本</p>}
                    </div>
                  </section>

                  <section className="share-panel" aria-label="评论">
                    <h3>评论</h3>
                    <form onSubmit={createComment}>
                      <textarea value={commentBody} onChange={(event) => setCommentBody(event.target.value)} placeholder="添加评论" />
                      <button className="primary" type="submit">发布</button>
                    </form>
                    <div className="share-list">
                      {comments.map((comment) => (
                        <div className={`comment-item ${comment.resolved ? 'resolved' : ''}`} key={comment.id}>
                          <strong>{comment.authorName}</strong>
                          <p>{comment.body}</p>
                          <small>{comment.resolved ? '已解决' : '未解决'}</small>
                          {canEdit && (
                            <button type="button" onClick={() => void toggleComment(comment)}>
                              {comment.resolved ? '重新打开' : '标记解决'}
                            </button>
                          )}
                        </div>
                      ))}
                      {comments.length === 0 && <p className="empty">暂无评论</p>}
                    </div>
                  </section>
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
      {preparedImport && (
        <div className="modal-backdrop" role="presentation">
          <section className="import-preview" role="dialog" aria-modal="true" aria-label="导入预览">
            <header className="modal-header">
              <div>
                <p className="eyebrow">{preparedImport.format}</p>
                <h2>导入预览：{preparedImport.title}</h2>
              </div>
              <button className="icon-button" type="button" onClick={() => setPreparedImport(undefined)} aria-label="关闭导入预览">
                <X size={18} />
              </button>
            </header>
            <div className="preview-surface" dangerouslySetInnerHTML={{ __html: preparedImport.html }} />
            <footer className="modal-actions">
              <button className="text-button" type="button" onClick={() => setPreparedImport(undefined)}>
                取消
              </button>
              <button className="primary" type="button" onClick={() => void confirmPreparedImport()}>
                创建并导入
              </button>
            </footer>
          </section>
        </div>
      )}
    </main>
  );
}

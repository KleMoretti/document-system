# API Contract

Both backends expose the same REST and WebSocket surface.

Operational endpoints:

- `GET /healthz` returns `{ "status": "ok" }` when the backend process is alive.
- `GET /readyz` returns `{ "status": "ready" }` when required dependencies are reachable, or HTTP 503 when the backend is not ready.

Errors use this shape:

```json
{
  "code": "UNAUTHORIZED",
  "message": "Missing or invalid token."
}
```

Roles:

- `owner`: full access, can share and delete
- `editor`: can read and edit
- `viewer`: can read and receive updates, cannot send edit updates

Environment switching is owned by the frontend `.env.local` file.

Document formats:

- The backend remains format-agnostic and persists the canonical collaborative Yjs state.
- Markdown, HTML, and TXT import/export are frontend boundary conversions.
- File import is a frontend preview-and-confirm flow. The backend document is created only after the user confirms the sanitized preview.
- Document templates are frontend-provided HTML seeds that enter the same initial import path as uploaded files.
- HTML and PDF exports may apply frontend-only style templates; Markdown and TXT exports remain content-only.
- PDF export is a frontend browser print flow; PDF import is intentionally out of scope.
- Imported files always create a new document and never overwrite an existing collaborative document.

Documents:

- `GET /api/documents` accepts `query` for title search and `status=active|deleted`; omitted `status` means `active`.
- `DELETE /api/documents/{docId}` soft deletes a document. `POST /api/documents/{docId}/restore` restores it and requires `owner`.
- `PATCH /api/documents/{docId}` renames a document and requires `owner` or `editor`.

Versions:

- `POST /api/documents/{docId}/versions` stores the current persisted Yjs update sequence with an optional `label`.
- `GET /api/documents/{docId}/versions` returns version summaries.
- `GET /api/documents/{docId}/versions/{versionId}` returns version metadata plus Base64 Yjs `updates`.
- `POST /api/documents/{docId}/versions/{versionId}/restore` replaces persisted document updates with that version and requires `owner` or `editor`.
- Version restore broadcasts WebSocket `document:restored`; active clients should reload the document before sending more updates.

Snapshots:

- Backends may return `snapshot` and `snapshotSeq` in `sync:init`.
- Clients must apply `snapshot` before `updates`.
- Editors may send `sync:snapshot` to compact old Yjs updates without changing the canonical document model.

Comments:

- Any user with document access can list comments, create comments, and reply.
- `owner` and `editor` can update comment body or `resolved` state.
- Comment mutations are REST-persisted; WebSocket `comment:*` notifications are optional broadcast hints and clients must ignore unknown event types.

# API Contract

Both backends expose the same REST and WebSocket surface.

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

Documents:

- `GET /api/documents` accepts `query` for title search and `status=active|deleted`; omitted `status` means `active`.
- `DELETE /api/documents/{docId}` soft deletes a document. `POST /api/documents/{docId}/restore` restores it and requires `owner`.
- `PATCH /api/documents/{docId}` renames a document and requires `owner` or `editor`.

Versions:

- `POST /api/documents/{docId}/versions` stores the current persisted Yjs update sequence with an optional `label`.
- `GET /api/documents/{docId}/versions` returns version summaries.
- `GET /api/documents/{docId}/versions/{versionId}` returns version metadata plus Base64 Yjs `updates`.
- `POST /api/documents/{docId}/versions/{versionId}/restore` replaces persisted document updates with that version and requires `owner` or `editor`.

Comments:

- Any user with document access can list comments, create comments, and reply.
- `owner` and `editor` can update comment body or `resolved` state.
- Comment mutations are REST-persisted; WebSocket `comment:*` notifications are optional broadcast hints and clients must ignore unknown event types.

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

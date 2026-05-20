# WebSocket 协议

连接地址：

```text
/ws/documents/{docId}
```

浏览器客户端通过 WebSocket 子协议传递 JWT，避免 token 进入 URL、浏览器历史或常规访问日志：

```ts
new WebSocket("/ws/documents/{docId}", ["bearer", "<jwt>"])
```

服务端为兼容旧客户端仍可接受 `?token=<jwt>`，但新客户端不应使用 URL 查询参数。所有消息为 JSON 文本。Yjs update 使用 Base64 编码。

## Operational Metrics

Java 和 Go 后端都暴露 `GET /metrics`，用于抓取 Prometheus text exposition 格式的运行指标。当前指标包括：

- `documentation_collab_http_requests_total`
- `documentation_collab_ws_connections_total`
- `documentation_collab_ws_connections_active`
- `documentation_collab_ws_messages_total`

## Client -> Server

```json
{
  "type": "sync:update",
  "docId": "uuid",
  "update": "base64-yjs-update"
}
```

```json
{
  "type": "presence:update",
  "docId": "uuid",
  "userId": "uuid",
  "displayName": "Ada",
  "color": "#2563eb"
}
```

## Server -> Client

```json
{
  "type": "sync:init",
  "docId": "uuid",
  "updates": ["base64-yjs-update"]
}
```

```json
{
  "type": "sync:update",
  "docId": "uuid",
  "userId": "uuid",
  "update": "base64-yjs-update"
}
```

```json
{
  "type": "presence:update",
  "docId": "uuid",
  "userId": "uuid",
  "displayName": "Ada",
  "color": "#2563eb"
}
```

```json
{
  "type": "error",
  "code": "FORBIDDEN",
  "message": "You do not have access to this document."
}
```

## Comment Events

Comments are persisted through REST. Backends may broadcast these events to active document clients after comment mutations; clients must ignore unknown event types for forward compatibility.

```json
{
  "type": "comment:created",
  "docId": "uuid",
  "commentId": "uuid",
  "comment": {
    "id": "uuid",
    "documentId": "uuid",
    "authorId": "uuid",
    "authorName": "Ada",
    "body": "Looks good",
    "resolved": false,
    "createdAt": "2026-05-11T00:00:00Z",
    "updatedAt": "2026-05-11T00:00:00Z",
    "replies": []
  }
}
```

```json
{
  "type": "comment:updated",
  "docId": "uuid",
  "commentId": "uuid",
  "comment": {}
}
```

```json
{
  "type": "comment:resolved",
  "docId": "uuid",
  "commentId": "uuid",
  "comment": {}
}
```

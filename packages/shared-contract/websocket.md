# WebSocket 协议

连接地址：

```text
/ws/documents/{docId}?token=<jwt>
```

所有消息为 JSON 文本。Yjs update 使用 Base64 编码。

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
  "commentId": "uuid"
}
```

```json
{
  "type": "comment:updated",
  "docId": "uuid",
  "commentId": "uuid"
}
```

```json
{
  "type": "comment:resolved",
  "docId": "uuid",
  "commentId": "uuid"
}
```

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

## 运维指标

Java 后端暴露 `GET /metrics`，用于抓取 Prometheus 文本格式的运行指标。当前指标包括：

- `documentation_collab_http_requests_total`
- `documentation_collab_ws_connections_total`
- `documentation_collab_ws_connections_active`
- `documentation_collab_ws_messages_total`
- `documentation_collab_ws_errors_total`
- `documentation_collab_ws_message_bytes_total`
- `documentation_collab_ws_slow_clients_total`
- `documentation_collab_ws_send_queue_depth_max`
- `documentation_collab_ws_broadcast_duration_ms_count|sum|max`
- `documentation_collab_ws_persist_duration_ms_count|sum|max`
- `documentation_collab_ws_batch_size_count|sum|max`

## 客户端到服务端

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

## 服务端到客户端

```json
{
  "type": "sync:init",
  "docId": "uuid",
  "snapshot": "base64-yjs-state-update",
  "snapshotSeq": 120,
  "updates": ["base64-yjs-update"]
}
```

`snapshot` 和 `snapshotSeq` 可选。客户端收到后必须先应用 `snapshot`，再按顺序应用 `updates`。未压缩的旧文档可以继续只返回 `updates`。

```json
{
  "type": "sync:snapshot",
  "docId": "uuid",
  "snapshot": "base64-yjs-state-update",
  "snapshotSeq": 120
}
```

`sync:snapshot` 由有编辑权限的客户端发送，用于压缩历史 Yjs update 序列。服务端仍保留 `snapshotSeq` 之后的增量 update，保证旧增量写入不丢失。

服务端只接受基于当前持久化状态的快照：`snapshotSeq` 必须等于当前快照序号加未压缩增量数，并且未压缩增量数必须达到后端阈值，默认 100。`sync:update` 和 `sync:snapshot` 解码后的二进制大小都不能超过 1 MiB。

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

```json
{
  "type": "document:restored",
  "docId": "uuid"
}
```

`document:restored` 表示服务端已用历史版本替换当前持久化状态。客户端应重新加载文档，避免继续基于旧本地状态写入。

## 评论事件

评论通过 REST 持久化。评论变更后，后端可以向当前活跃文档客户端广播这些事件；客户端必须忽略未知事件类型，以保持向前兼容。

在微服务拆分模式下，评论事件和 `document:restored` 由 document REST 服务通过 Redis Pub/Sub 发布，realtime WebSocket 服务订阅后广播给本机连接。`sync:update` 和 `presence:update` 仍由 WebSocket 连接所在实例直接发布到 Redis。两边的 Redis 频道统一为 `doc:{docId}`，通过 `source` 字段去重避免回环。

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

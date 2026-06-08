# API 契约说明

本说明按当前 Java 后端实现整理，覆盖前端调用 Java API / WebSocket 时需要依赖的请求、响应和权限语义。

## 运维端点

- `GET /healthz`：后端进程存活时返回 `{ "status": "ok" }`。
- `GET /readyz`：Java 后端调用 `repository.ping()` 检查 MySQL；依赖可用时返回 `{ "status": "ready" }`；依赖不可用时返回 HTTP 503 和 `{ "status": "not_ready" }`。
- `GET /metrics`：返回 Java 后端手写的 Prometheus 文本指标；`/metrics` 自身不会计入 HTTP 请求计数。

错误响应统一使用以下结构：

```json
{
  "code": "UNAUTHORIZED",
  "message": "Missing or invalid token."
}
```

## 角色模型

- `owner`：完整权限，可以分享、编辑、删除和恢复文档。
- `editor`：可以读取、编辑、重命名、保存/恢复版本，并更新评论正文或解决状态。
- `viewer`：可以读取并接收实时更新，不能发送编辑 update。

REST 和 WebSocket 都以后端校验为准。前端隐藏按钮只用于体验，不能作为安全边界。

## 认证

- 注册：`POST /api/auth/register` 要求 `email`、`password`、`displayName` 非空；密码使用 BCrypt 哈希后写入 `users`；邮箱重复返回 `USER_EXISTS`。
- 登录：`POST /api/auth/login` 使用 email 查询用户，并用 BCrypt 校验密码；邮箱不存在和密码错误统一返回 `UNAUTHORIZED`。
- 密钥：`JWT_SECRET` 必须显式设置；为空或等于 `change-this-development-secret` 时 Java 后端拒绝启动。
- JWT：Java 后端使用 HS256 签发，payload 包含 `sub`、`email`、`exp`；`JWT_TTL` 默认 `2h`。
- REST 鉴权：除注册和登录外，业务接口通过 `Authorization: Bearer <jwt>` 传递令牌。
- WebSocket 鉴权：新客户端通过 `Sec-WebSocket-Protocol: bearer, <jwt>` 传递令牌，服务端仍兼容 `?token=<jwt>`。

## 文档格式

- 后端保持格式无关，只持久化规范的协同编辑 Yjs 状态。
- Markdown、HTML、TXT 的导入导出属于前端边界转换。
- 文件导入是前端预览并确认的流程。只有用户确认经过清洗的预览后，才会创建后端文档。
- 文档模板由前端提供 HTML 初始内容，进入与上传文件相同的初始化路径。
- HTML 和 PDF 导出可以应用前端样式模板；Markdown 和 TXT 导出保持内容导向。
- PDF 导出依赖浏览器打印流程；PDF 导入当前明确不支持。
- 导入文件总是创建新文档，不覆盖已有协同文档。

## 文档

- `GET /api/documents` 支持 `query` 标题搜索和 `status=active|deleted`；省略 `status` 时默认查询 `active`。
- `DELETE /api/documents/{docId}` 对文档做软删除。
- `POST /api/documents/{docId}/restore` 恢复软删除文档，要求 `owner` 权限。
- `PATCH /api/documents/{docId}` 重命名文档，要求 `owner` 或 `editor` 权限。

## 版本

- `POST /api/documents/{docId}/versions` 保存当前已持久化的 Yjs update 序列，可带可选 `label`。
- `GET /api/documents/{docId}/versions` 返回版本摘要列表。
- `GET /api/documents/{docId}/versions/{versionId}` 返回版本元数据和 Base64 编码的 Yjs `updates`。
- `POST /api/documents/{docId}/versions/{versionId}/restore` 用指定版本替换当前持久化 update，要求 `owner` 或 `editor` 权限。
- 版本恢复会广播 WebSocket `document:restored`；在线客户端应重新加载文档后再继续发送 update。

## 快照

- 后端可以在 `sync:init` 中返回 `snapshot` 和 `snapshotSeq`。
- 客户端必须先应用 `snapshot`，再按顺序应用 `updates`。
- 编辑者可以发送 `sync:snapshot` 压缩旧 Yjs updates；这不会改变文档的协同数据模型。
- 服务端只接受基于当前持久化状态的快照：`snapshotSeq` 必须等于当前快照序号加未压缩增量数，并且未压缩增量数必须达到后端阈值，默认 100。
- `sync:update` 与 `sync:snapshot` 解码后的二进制大小都不能超过 1 MiB。

## 评论

- 任何拥有文档访问权的用户都可以查看评论、创建评论和回复。
- `owner` 和 `editor` 可以更新评论正文或 `resolved` 状态。
- 评论变更通过 REST 持久化；WebSocket `comment:*` 事件只是广播提示。客户端必须忽略未知事件类型，保持向前兼容。

## 内部接口（服务间通信）

以下接口仅在 auth 或 all 服务角色下暴露，通过 `X-Service-Token` 请求头校验调用方身份，校验值由 `SERVICE_TOKEN` 环境变量配置。

### 按邮箱查询用户

```
GET /internal/users/by-email?email=ada@example.com
X-Service-Token: <SERVICE_TOKEN>
```

返回 `User` JSON 对象；用户不存在时返回 404。

### 批量查询用户

```
GET /internal/users?ids=id1,id2,id3
X-Service-Token: <SERVICE_TOKEN>
```

返回 `{ "id1": User, "id2": User, ... }` JSON 对象；不存在的 ID 不会出现在结果中。

### 内部健康检查

```
GET /internal/healthz
```

返回 `{ "status": "ok" }`，无需 service token。

## 微服务拆分说明

Java 后端支持通过 `APP_SERVICE_ROLE` 环境变量按角色部署：

- 仅 `auth` 角色时，外部接口只暴露 `/api/auth/register`、`/api/auth/login`、`/api/me` 和内部接口。
- 仅 `document` 角色时，外部接口只暴露 `/api/documents/**`，不启动 WebSocket。评论变更和版本恢复通过 Redis 发布事件。
- 仅 `realtime` 角色时，外部接口只暴露 `/ws/documents/{docId}`，不启动 REST 文档接口。
- `all`（默认）角色时，所有接口和 WebSocket 合并在一个进程中。

外部 REST 和 WebSocket 路径在所有部署模式下保持不变。

# 代码审查报告

**项目**: Documentation Collab  
**审查范围**: 全栈项目（Go 后端、Java 后端、React 前端、基础设施）  
**审查日期**: 2026-05-11

---

## 📋 项目概述

这是一个在线文档协同编辑平台，包含两个后端实现（Go + Java）、一个 React 前端、MySQL + Redis 数据存储，以及 WebSocket 实时协作功能。

---

## 🔴 严重问题（Critical）

### 1. 用户不存在时泄露信息（Java 后端）

**文件**: `apps/backend-java/src/main/java/com/example/docs/AuthController.java:42-46`

```java
var user = repository.findUserForLogin(req.email());
if (!passwordEncoder.matches(req.password(), user.passwordHash())) {
```

如果邮箱不存在，`findUserForLogin` 抛出 `EmptyResultDataAccessException`，会被 `ErrorAdvice` 捕获并返回 404 错误消息 "Resource not found"。这与 Go 后端行为不同：

**Go 后端** (`apps/backend-go/internal/server.go:73`)：
```go
user, hash, err := s.store.FindUserForLogin(r.Context(), req.Email)
if err != nil || bcrypt.CompareHashAndPassword([]byte(hash), []byte(req.Password)) != nil {
    writeError(w, http.StatusUnauthorized, "INVALID_CREDENTIALS", "Email or password is incorrect.")
```

**问题**: Go 版将"用户不存在"和"密码错误"合并为同一条模糊消息，避免用户枚举攻击。Java 版应采用相同策略。

### 2. WebSocket 连接验证失败处理不当

**文件**: `apps/backend-java/src/main/java/com/example/docs/DocumentSocketHandler.java:35`

```java
var claims = jwtManager.verify(query(session.getUri(), "token"));
```

如果 JWT 验证失败，`verify` 抛出 `IllegalArgumentException`，而 `afterConnectionEstablished` 签名是 `throws Exception`。异常会被 Spring WebSocket 框架捕获并静默关闭连接，**不会向客户端返回有意义的错误信息**。

Go 后端处理更完善 (`apps/backend-go/internal/ws.go:78-80`)：
```go
claims, err := s.auth.Verify(token)
if err != nil {
    http.Error(w, "invalid token", http.StatusUnauthorized)
    return
}
```

**建议**: 捕获验证异常，通过 `session.sendMessage()` 发送明确的错误消息，然后关闭连接。

---

## 🟠 高优先级问题（High）

### 3. 死代码：`notFound` 和 `errorsIs` 函数

**文件**: `apps/backend-go/internal/server.go:284-290`

```go
func notFound(err error) bool {
    return errorsIs(err, sql.ErrNoRows)
}

func errorsIs(err, target error) bool {
    return err == target
}
```

- `notFound()` 在整个代码库中**从未被调用**（死代码）。
- `errorsIs()` 自定义实现仅使用 `==` 比较，不支持 Go 1.13+ 的错误包装（wrapping）。应直接使用标准库的 `errors.Is()`。
- `getDocument` 查询返回 `sql.ErrNoRows` 时直接传给调用方作为 `err`，但上层 `handleDocument` 将其视为通用 "NOT_FOUND" 错误，丢失了区分度。

### 4. WebSocket 更新失败后连接状态不一致

**文件**: `apps/backend-go/internal/ws.go:96-107`

```go
updates, err := s.store.LoadUpdates(r.Context(), docID)
if err == nil {
    encoded := make([]string, 0, len(updates))
    // ...
    _ = conn.WriteJSON(WSMessage{Type: "sync:init", ...})
}

go writePump(client)
readPump(r.Context(), s, client, role)
```

如果 `LoadUpdates` 失败（`err != nil`），代码**跳过发送 `sync:init` 消息但仍然建立连接**。客户端会认为连接成功，但没有收到初始文档状态，导致编辑器显示空白或不同步状态。

**建议**: 加载失败时向客户端发送错误消息或关闭连接。

### 5. `shareDocument` 未处理用户不存在的情况

**文件**: `apps/backend-java/src/main/java/com/example/docs/AppRepository.java:126-128`

```java
var userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, req.email());
```

如果邮箱不存在，`queryForObject` 抛出 `EmptyResultDataAccessException`，会被 `ErrorAdvice` 捕获返回 404 "Resource not found"。错误语义不明确——用户应该收到 "用户不存在" 的提示。

Go 后端也存在同样问题 (`apps/backend-go/internal/store.go:117`)。

### 6. 跨站 WebSocket 劫持风险

**文件**: `apps/backend-go/internal/ws.go:70-72`

```go
var upgrader = websocket.Upgrader{
    CheckOrigin: func(r *http.Request) bool { return true },
}
```

**文件**: `apps/backend-java/src/main/java/com/example/docs/WebSocketConfig.java:19`

```java
registry.addHandler(handler, "/ws/documents/{docId}").setAllowedOrigins("*");
```

两处都允许**任意来源**的 WebSocket 连接。虽然 JWT 验证提供了认证，但放宽 Origin 检查可能使第三方网站能够发起 WebSocket 连接并尝试暴力枚举文档 ID。

---

## 🟡 中优先级问题（Medium）

### 7. 消息丢失风险：无背压机制

**文件**: `apps/backend-go/internal/ws.go:59-68`

```go
func (h *Hub) BroadcastRaw(docID string, payload []byte) {
    for client := range h.clients[docID] {
        select {
        case client.send <- payload:
        default:  // 非阻塞发送，缓冲区满时丢弃
        }
    }
}
```

客户端 `send` 通道缓冲区大小为 32。当客户端处理速度跟不上消息产生速度时，**消息会被静默丢弃**。在快速连续编辑场景下，可能导致文档状态不一致。

**建议**: 
- 增加缓冲区大小
- 实现背压机制（如客户端处理速度过慢时强制断开重连）
- 至少记录丢失日志

### 8. 硬编码的开发凭据和 API 地址

**文件**: `apps/web/src/App.tsx:15-17`
```typescript
const [email, setEmail] = useState('ada@example.com');
const [password, setPassword] = useState('password123');
const [displayName, setDisplayName] = useState('Ada');
```

**文件**: `apps/web/src/api.ts:3`
```typescript
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
```

**文件**: `apps/web/src/CollaborativeEditor.tsx:8`
```typescript
const WS_BASE = import.meta.env.VITE_WS_BASE_URL ?? 'ws://localhost:8080';
```

**问题**: 
- 默认凭据在源码中明文暴露，如果推送到公开仓库有风险。
- WebSocket 的 `WS_BASE` 硬编码为 `8080`，如果用户将 API 设置为 Go 后端的 `8081`，WebSocket 仍然连接 `8080`。

**建议**: 
- 使用 `.env` 文件管理开发环境配置（已在 `.env.example` 预留）。
- 从 `API_BASE` 自动推导 WebSocket URL。

### 9. CORS 配置过于宽松

**Go** (`server.go:244-246`):
```go
w.Header().Set("Access-Control-Allow-Origin", "*")
```

**Java** (`Application.java:47`):
```java
registry.addMapping("/api/**").allowedOrigins("*")
```

生产环境中应限制为具体的域名。

### 10. JWT 密钥默认值

**Go** (`config.go:33`): `JWT_SECRET` 默认为 `"change-this-development-secret"`  
**Java** (`application.yml:14`): `JWT_SECRET` 默认为同值

**影响**: 如果部署时未更改密钥，任何人都可以伪造 JWT 令牌。

### 11. Go 版缺少文档重命名的权限检查竞争条件

**文件**: `apps/backend-go/internal/server.go:122-145`

`documentByID` 方法中，`GetDocument` 查询后检查权限，然后 `handleDocument` 中再次查询。但在 `handleDocument:PATCH` 中，`RenameDocument` 没有检查 `doc.Role` 是否仍然是可编辑的。在高并发场景下，如果用户权限在两次调用间被撤销，仍可能成功重命名。

Java 版有同样的问题，但 Spring 的 `@Transactional` 提供了更好的事务边界。

---

## 🔵 低优先级问题（Low）

### 12. 重复 SQL 定义

`packages/shared-contract/sql/schema.mysql.sql` 和 `apps/backend-java/src/main/resources/schema.sql` **内容完全一致**。建议引用单一来源。

### 13. `document_snapshots` 表已定义但未使用

SQL schema 中定义了 `document_snapshots` 表，但两个后端都没有实现快照功能。如果不需要应移除表定义，避免维护负担。

### 14. 测试覆盖率低

**Go 后端**: 只有 `auth_test.go` 和 `roles_test.go`，覆盖 2 个文件。缺少 `store_test.go`、`server_test.go` 等。

**Java 后端**: 缺少 `DocumentControllerTest`、`AppRepositoryTest`、`DocumentSocketHandlerTest`。

**前端**: 只有 `api.test.ts`，缺少组件测试和集成测试。

### 15. Go 版 `AppRepository.findUserForLogin` 缺少上下文

**Go** (`store.go:29`): `FindUserForLogin` 未在 `document_permissions` 表中验证用户是否有权访问特定文档——但这本身不是问题，因为 `FindUserForLogin` 是用于认证。

### 16. Go 版的 `docID` 路径解析不安全

**ws.go**:75**:
```go
docID := strings.TrimPrefix(r.URL.Path, "/ws/documents/")
```

没有对 `docID` 进行格式验证（如 UUID 格式校验、长度限制），可能导致无效查询到数据库。

### 17. 前端缺少 WebSocket 重连逻辑

`CollaborativeEditor.tsx` 中，当 WebSocket 连接断开（`close` 或 `error` 事件）时，状态设为 `offline`，但没有尝试自动重连。

### 18. Docker Compose 缺少 Go 后端服务

`infra/docker-compose.yml` 只定义了 Redis 和 MySQL，缺少两个后端服务的定义。

---

## 📊 总结

| 级别 | 数量 |
|------|------|
| 🔴 严重 | 2 |
| 🟠 高 | 4 |
| 🟡 中 | 5 |
| 🔵 低 | 7 |
| **总计** | **18** |

### 关键修复优先级排序

1. **统一认证错误消息**（Java 后端登录接口）— 防止用户枚举攻击
2. **WebSocket 验证错误处理**（Java）— 客户端需收到明确的错误反馈
3. **死代码清理**（Go `notFound`/`errorsIs`）
4. **LoadUpdates 失败后的连接状态处理**（Go WebSocket）
5. **WebSocket CORS 来源限制**
6. **消息丢失和背压机制**
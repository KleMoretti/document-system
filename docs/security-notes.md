# 安全说明

当前项目仍是简历/作品集级别的协同编辑系统，不是完整加固后的生产部署。以下默认安全设置需要在真实环境中按部署条件复核和调整。

## 认证

- REST API 使用 `Authorization: Bearer <jwt>`。由于浏览器不会自动携带这类凭据，此认证模式下不需要额外 CSRF 令牌。
- 对浏览器持有的 JWT 来说，XSS 仍是主要风险。前端会在导入 HTML 进入编辑器前做清洗；生产部署还应在反向代理层增加严格的 CSP 内容安全策略。
- Java 后端使用 HS256 签发 JWT，payload 包含 `sub`、`email` 和 `exp`。`JwtManager.verify()` 会校验 token 结构、签名和过期时间。
- `JWT_SECRET` 必须显式设置。Java 应用在密钥为空或等于 `change-this-development-secret` 时拒绝启动。
- JWT 生命周期由 `JWT_TTL` 控制，默认值为 `2h`。
- 密码哈希成本由 `BCRYPT_COST` 控制，默认值为 `12`。
- 注册和登录由 `AuthController` 处理。注册要求 `email`、`password`、`displayName` 非空；登录时邮箱不存在和密码错误都返回同一个未授权错误，避免暴露账号是否存在。
- REST CORS 只配置在 `/api/**`，允许来源来自 `ALLOWED_ORIGINS`，允许请求头为 `Authorization` 和 `Content-Type`。

## WebSocket 令牌传递

新客户端通过 WebSocket 子协议列表传递 JWT：

```ts
new WebSocket("/ws/documents/{docId}", ["bearer", token])
```

服务端仍兼容旧的 `?token=` 查询参数，但新客户端不应继续使用这种方式，因为 URL 更容易出现在浏览器历史记录和代理日志中。

WebSocket Origin 白名单同样来自 `ALLOWED_ORIGINS`。连接建立后，Java 后端会校验 docId 必须是 UUID、JWT 必须有效，并查询用户在该文档中的角色；没有访问权会返回 `FORBIDDEN` 并关闭连接。

## Redis 连接

当部署环境提供启用 TLS 的 Redis 端点时，可以通过 `REDIS_TLS=true` 开启 Redis TLS。本地 Docker Compose 为了开发便利，在私有 Compose 网络内使用明文 Redis。

## 服务间认证

在微服务拆分部署模式下，document 服务通过 HTTP 调用 auth 服务的内部接口来解析用户信息。这些内部接口不依赖 JWT，而是通过 `X-Service-Token` 请求头校验调用方身份：

- `SERVICE_TOKEN` 环境变量在 auth 和 document 服务之间共享，作为简单的预共享密钥。
- 内部接口的 CORS 配置独立于 `/api/**`，只允许 `X-Service-Token` 和 `Content-Type` 请求头。
- 在 `all` 单体模式下，内部接口仍然注册；如果未配置 `SERVICE_TOKEN`，用户查询类内部接口会拒绝所有请求。即使配置了 `SERVICE_TOKEN`，也不应将 `all` 模式的内部端口暴露到外部网络。

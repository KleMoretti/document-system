# Java 全栈简历学习路线

本文基于本仓库的在线文档协同编辑系统生成，目标是把项目学习、简历包装和面试表达统一起来。项目核心栈包括 Java 21、Spring Boot 3、React、TypeScript、Vite、Tiptap、Yjs、MySQL、Redis、WebSocket、JWT、Docker 和测试体系。

## 1. 简历定位

建议岗位定位：

- Java 全栈开发工程师
- Java 后端开发工程师，具备前端协同编辑经验
- 企业协同办公 / 文档协作 / 实时系统方向 Java 开发

简历项目名称可以写为：

```text
在线文档协同编辑系统
```

推荐项目一句话描述：

```text
基于 React + Tiptap + Yjs、Java 21 + Spring Boot、MySQL、Redis 和 WebSocket 实现的在线文档协同编辑系统，支持用户认证、文档权限、实时协作、在线状态、版本管理、评论协作、软删除恢复、导入导出和 Prometheus 指标。
```

## 2. 技术主线

学习顺序建议按项目链路推进，而不是按技术名词孤立学习：

1. 前后端基础链路：React 页面调用 Spring Boot REST API。
2. 用户认证链路：注册、登录、JWT 签发、接口鉴权。
3. 文档权限链路：owner、editor、viewer 三类角色控制读写、分享、删除。
4. 数据持久化链路：MySQL 表设计、JdbcTemplate 查询、事务和行锁。
5. 实时协作链路：WebSocket 连接、Yjs update、Base64 编码、服务端广播。
6. 多实例扩展链路：Redis Pub/Sub 做跨实例消息转发。
7. 产品能力链路：版本、评论、导入导出、软删除恢复。
8. 工程能力链路：契约文档、测试、Docker、指标和运维。

## 3. 阶段路线

### 阶段一：项目启动和全局架构

学习目标：

- 理解 monorepo 中当前 Java 栈相关结构：`apps/web`、`apps/backend-java`、`packages/shared-contract`、`docs`。
- 理解前后端契约边界：前端只依赖 REST 和 WebSocket 契约，不依赖 Java 后端内部实现细节。
- 能画出系统架构图：React/Yjs 前端、Java Spring Boot 后端、MySQL、Redis。

重点文件：

- `README.md`
- `docs/architecture.md`
- `docs/api-contract.md`
- `packages/shared-contract/openapi.yml`
- `packages/shared-contract/websocket.md`

简历可写：

```text
负责梳理并实现在线文档协作系统的前后端分层架构，前端通过 REST/WebSocket 契约连接 Java Spring Boot 后端，保证接口字段、权限语义和实时消息行为一致。
```

面试要能回答：

- 为什么前端不能依赖后端内部实现细节？
- 契约在多人协作和前后端联调中解决了什么问题？
- Java 后端、前端类型和文档如何避免接口语义分叉？

### 阶段二：Java Spring Boot REST API

学习目标：

- 掌握 Spring Boot Controller、请求体、路径参数、请求头、响应状态码。
- 掌握统一错误响应格式。
- 理解注册、登录、获取当前用户、文档列表、创建文档、重命名、删除、恢复、分享、版本、评论等 REST 能力。

重点文件：

- `apps/backend-java/src/main/java/com/example/docs/AuthController.java`
- `apps/backend-java/src/main/java/com/example/docs/DocumentController.java`
- `apps/backend-java/src/main/java/com/example/docs/ErrorAdvice.java`
- `apps/backend-java/src/main/java/com/example/docs/Models.java`

练习任务：

- 手动梳理每个接口的 URL、HTTP 方法、权限要求、成功响应和错误响应。
- 对照 `docs/api-contract.md` 检查实现是否符合契约。
- 为一个接口补充测试用例，例如 viewer 不能重命名文档。

简历可写：

```text
基于 Spring Boot 3 实现用户认证、文档管理、分享权限、版本管理和评论协作等 REST API，统一错误响应格式，保证 Java 后端实现、接口文档和前端调用对齐。
```

### 阶段三：JWT、密码安全和 RBAC 权限

学习目标：

- 理解 BCrypt 密码哈希和明文密码不落库原则。
- 理解 JWT 的 header、payload、signature、exp 过期时间。
- 掌握 Bearer 令牌接口鉴权。
- 掌握 owner、editor、viewer 权限边界。

重点文件：

- `apps/backend-java/src/main/java/com/example/docs/AuthController.java`
- `apps/backend-java/src/main/java/com/example/docs/JwtManager.java`
- `apps/backend-java/src/main/java/com/example/docs/Roles.java`
- `docs/security-notes.md`

权限规则：

- owner：完整权限，可以分享、删除、恢复。
- editor：可读写，可以重命名、编辑、恢复版本、更新评论。
- viewer：只读，可接收协同更新，不能发送编辑更新。

简历可写：

```text
设计并实现基于 JWT + BCrypt + RBAC 的认证授权体系，按 owner/editor/viewer 控制文档读写、分享、删除和 WebSocket 编辑权限。
```

面试要能回答：

- JWT 为什么要设置过期时间？
- BCrypt 和普通哈希有什么区别？
- REST 鉴权和 WebSocket 鉴权有什么不同？
- viewer 为什么可以连接 WebSocket 但不能发送编辑 update？

### 阶段四：MySQL 表设计、事务和行锁

学习目标：

- 理解用户、文档、权限、Yjs 更新、版本、评论、回复的表关系。
- 掌握外键、唯一索引、普通索引、复合主键。
- 理解协同编辑更新追加写入为什么需要事务和行锁。
- 理解 `SELECT ... FOR UPDATE` 在并发写入中的作用。

重点文件：

- `packages/shared-contract/sql/schema.mysql.sql`
- `apps/backend-java/src/main/java/com/example/docs/AppRepository.java`

关键表：

- `users`：用户身份。
- `documents`：文档元数据，包含 `deleted_at` 软删除字段。
- `document_permissions`：文档和用户的角色关系。
- `document_sequences`：按文档记录下一次可用 seq，支撑批量写入时连续分配序号。
- `document_updates`：按 `seq` 保存 Yjs 增量更新。
- `document_versions`：保存某一时刻的更新序列快照。
- `document_comments`、`document_comment_replies`：评论和回复。

简历可写：

```text
设计 MySQL 文档协作数据模型，使用 document_updates 按序保存 Yjs 增量更新，并通过 document_sequences、事务和行锁保证并发写入时序列号递增一致。
```

面试要能回答：

- 为什么 `document_updates` 要有 `(document_id, seq)` 唯一约束？
- 多个用户同时编辑同一文档时，如何避免 seq 冲突？
- 软删除为什么比物理删除更适合协同文档？
- 版本恢复为什么要删除旧 update 再按版本快照重建？

### 阶段五：WebSocket 和实时协同编辑

学习目标：

- 理解 WebSocket 生命周期：连接建立、消息处理、断开清理。
- 理解 `sync:init`、`sync:update`、`presence:update`、`error` 消息。
- 理解 Yjs update 是协同状态的增量，不是普通文本内容。
- 掌握 Base64 传输二进制 update 的原因。
- 理解只读用户、非法文档 ID、无效令牌的拒绝逻辑。

重点文件：

- `apps/backend-java/src/main/java/com/example/docs/DocumentSocketHandler.java`
- `packages/shared-contract/websocket.md`
- `apps/web/src/CollaborativeEditor.tsx`

核心流程：

1. 前端使用 `new WebSocket(url, ["bearer", token])` 建立连接。
2. 后端解析 docId，校验 JWT，查询用户在文档中的角色。
3. 后端加载历史 Yjs updates，发送 `sync:init`。
4. 前端应用历史 update，开始监听本地 Yjs 更新。
5. editor 用户发送 `sync:update`，后端持久化后广播给其他客户端。
6. viewer 用户只能接收更新，发送编辑更新会收到 `FORBIDDEN`。

简历可写：

```text
实现 WebSocket 实时协作通道，服务端在连接阶段完成 JWT 与文档权限校验，加载历史 Yjs update 初始化客户端，并对编辑增量进行持久化和广播。
```

### 阶段六：Redis Pub/Sub 多实例广播

学习目标：

- 理解单实例本地广播和多实例广播的区别。
- 理解 Redis Pub/Sub 在实时协同场景中的作用。
- 理解 source id 去重，避免本实例重复消费自己发布的消息。
- 理解 Pub/Sub 不保证持久化，因此不能替代 MySQL。

重点文件：

- `apps/backend-java/src/main/java/com/example/docs/RedisBus.java`
- `apps/backend-java/src/main/java/com/example/docs/DocumentSocketHandler.java`

简历可写：

```text
使用 Redis Pub/Sub 实现 WebSocket 消息跨实例广播，结合 MySQL 持久化保证协同编辑消息既能实时分发又能在客户端重连后恢复。
```

面试要能回答：

- 为什么不能只靠 WebSocket 内存广播？
- Redis Pub/Sub 丢消息怎么办？
- 为什么 MySQL 是状态来源，Redis 只是广播通道？

### 阶段七：React、TypeScript、Tiptap 和 Yjs 前端

学习目标：

- 掌握 React hooks、组件状态、effect 生命周期。
- 掌握 TypeScript 类型约束。
- 理解 Tiptap 编辑器和 Yjs Collaboration 扩展。
- 理解 WebSocket 自动重连、在线状态、只读态、导入导出。

重点文件：

- `apps/web/src/App.tsx`
- `apps/web/src/CollaborativeEditor.tsx`
- `apps/web/src/api.ts`
- `apps/web/src/config.ts`
- `apps/web/src/documentFormats.ts`
- `apps/web/src/exportStyles.ts`

简历可写：

```text
基于 React + TypeScript + Tiptap + Yjs 实现协同富文本编辑器，支持自动重连、在线状态展示、只读模式、Markdown/HTML/TXT/PDF 导入导出和前端模板初始化。
```

面试要能回答：

- 为什么导入导出放在前端边界，而不是后端处理？
- 前端如何避免把远端 update 再次发送回服务端？
- 断线重连后如何恢复文档状态？

### 阶段八：版本、评论和产品能力

学习目标：

- 理解版本是服务器持久化 Yjs update 序列的快照。
- 理解评论是 REST 持久化，WebSocket 只做轻量通知。
- 理解文档搜索、删除、恢复、分享等协同产品能力。

重点文件：

- `apps/backend-java/src/main/java/com/example/docs/DocumentController.java`
- `apps/backend-java/src/main/java/com/example/docs/AppRepository.java`
- `docs/api-contract.md`

简历可写：

```text
实现文档版本管理和评论协作能力，版本保存当前 Yjs update 序列快照，评论通过 REST 持久化并通过 WebSocket 发送失效通知，客户端按需刷新。
```

### 阶段九：测试、观测和部署

学习目标：

- 掌握 Java 单元测试和 Spring Boot 测试。
- 掌握前端 Vitest 测试。
- 理解 Prometheus 文本指标。
- 理解 Docker Compose 本地一键启动。

重点文件：

- `apps/backend-java/src/test/java/com/example/docs/*`
- `apps/web/src/*.test.ts`
- `apps/backend-java/src/main/java/com/example/docs/MetricsController.java`
- `apps/backend-java/src/main/java/com/example/docs/MetricsFilter.java`
- `docker-compose.yml`
- `docs/operability.md`

常用验证命令：

```powershell
npm run test:web
npm run build:web
npm run test:java
```

简历可写：

```text
补充认证、权限、WebSocket、指标和前端格式转换测试，使用 Docker Compose 编排 Web、Java API、MySQL 和 Redis，提供 Prometheus 格式运行指标。
```

## 4. 四周学习安排

### 第 1 周：跑通项目和 REST 主链路

- 第 1 天：阅读 `README.md`、`docs/architecture.md`，启动前端和 Java 后端。
- 第 2 天：梳理注册、登录、`/api/me`。
- 第 3 天：梳理文档创建、列表、详情、重命名。
- 第 4 天：梳理角色权限和错误响应。
- 第 5 天：跑 Java 测试，补充一条权限测试。
- 第 6 天：整理简历项目背景和技术栈。
- 第 7 天：用自己的话讲完整 REST 链路。

### 第 2 周：数据库、事务和版本

- 第 1 天：画出 MySQL ER 图。
- 第 2 天：理解 `document_permissions` 的角色模型。
- 第 3 天：理解 `document_updates` 的 append-only 设计。
- 第 4 天：理解 `SELECT ... FOR UPDATE` 和 seq 生成。
- 第 5 天：理解版本保存和恢复。
- 第 6 天：整理数据库相关简历 bullet。
- 第 7 天：模拟回答并发写入、索引、事务问题。

### 第 3 周：WebSocket、Yjs 和 Redis

- 第 1 天：梳理 WebSocket 连接鉴权。
- 第 2 天：梳理 `sync:init` 和 `sync:update`。
- 第 3 天：理解 Yjs update 和 Base64 传输。
- 第 4 天：理解前端自动重连和只读模式。
- 第 5 天：理解 Redis Pub/Sub 跨实例广播。
- 第 6 天：整理实时协作相关简历 bullet。
- 第 7 天：模拟回答实时协同系统设计题。

### 第 4 周：前端产品能力、测试和简历打磨

- 第 1 天：梳理 Tiptap 编辑器组件。
- 第 2 天：梳理导入导出和模板初始化。
- 第 3 天：梳理评论 REST + WebSocket 通知。
- 第 4 天：梳理指标和 Docker Compose。
- 第 5 天：跑前端测试和构建。
- 第 6 天：形成完整项目 STAR 表达。
- 第 7 天：根据问答文档做一轮模拟面试。

## 5. 简历项目要点模板

可根据实际掌握程度选择 4 到 6 条：

```text
- 负责在线文档协同编辑系统核心后端开发，基于 Java 21、Spring Boot 3、JdbcTemplate、MySQL、Redis 和 WebSocket 实现文档协作能力。
- 设计 owner/editor/viewer 三级 RBAC 权限模型，覆盖文档读写、分享、软删除、恢复、版本恢复和 WebSocket 编辑权限。
- 实现 Yjs 增量更新持久化方案，将协同编辑 update 按 document_id + seq 批量追加写入 MySQL，并通过 document_sequences、事务和行锁保证并发写入顺序。
- 实现 WebSocket 实时同步通道，支持连接鉴权、历史 update 初始化、编辑增量广播、在线状态同步和错误消息标准化。
- 使用 Redis Pub/Sub 实现多实例 WebSocket 消息广播，结合 MySQL 作为最终状态来源，支持客户端断线重连后的状态恢复。
- 基于 React + TypeScript + Tiptap + Yjs 实现协同富文本编辑前端，支持只读模式、自动重连、在线状态、导入导出和前端模板初始化。
- 维护 REST/WebSocket/SQL 契约，保证 Java 后端、前端类型和文档中的接口与业务语义一致。
- 补充认证、权限、WebSocket、指标和前端格式转换测试，并使用 Docker Compose 编排 Web、Java API、MySQL、Redis 本地环境。
```

## 6. 面试复盘标准

准备完成后，至少能稳定讲清楚：

- 这个项目解决什么业务问题。
- 你负责了哪些模块。
- 为什么选择 Yjs，而不是自己合并文本。
- Java 后端如何保证权限和数据一致性。
- WebSocket 如何鉴权、初始化、广播和处理错误。
- MySQL、Redis 在系统里的边界分别是什么。
- 如何保证 Java 后端、前端类型和契约文档一致。
- 如果用户量增加，系统瓶颈在哪里，怎么优化。

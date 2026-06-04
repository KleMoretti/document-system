# Java 全栈简历问答

本文基于本仓库的在线文档协同编辑系统生成，用于准备 Java 全栈简历项目追问。回答时建议坚持一个原则：先讲业务目标，再讲技术方案，最后讲权衡和结果。

## 1. 项目介绍类

### Q1：请介绍一下你简历上的在线文档协同编辑系统。

参考回答：

这是一个在线文档协同编辑系统，核心目标是让多个用户可以在浏览器里共同编辑同一篇富文本文档。前端使用 React、TypeScript、Tiptap 和 Yjs，Java 后端使用 Spring Boot 3、JdbcTemplate、MySQL、Redis 和 WebSocket。

系统支持用户注册登录、JWT 鉴权、文档创建和分享、owner/editor/viewer 权限控制、实时协同编辑、在线状态、版本保存和恢复、评论协作、软删除恢复、导入导出以及 Prometheus 指标。架构上前端只依赖共享 REST 和 WebSocket 契约，因此同一个前端可以切换 Java 或 Go 后端。

追问方向：

- 你主要负责哪一块？
- 为什么要做 Java 和 Go 两套后端？
- 项目里最难的点是什么？

### Q2：这个项目最能体现你 Java 能力的地方是什么？

参考回答：

我认为主要体现在三块。第一是 Spring Boot REST API 的完整业务建模，包括认证、文档、权限、版本和评论。第二是协同编辑 update 的持久化设计，服务端用事务和行锁保证同一文档下 seq 按顺序写入，避免并发编辑导致序号冲突。第三是 WebSocket 实时同步通道，连接时做 JWT 和文档权限校验，初始化时加载历史 Yjs update，编辑时持久化并广播。

这些能力不是单纯 CRUD，而是涉及权限、安全、并发、一致性、实时通信和可观测性。

追问方向：

- 为什么不用 JPA？
- `@Transactional` 用在了哪里？
- 行锁具体锁的是什么？

### Q3：如果让你用一句话写到简历里，你会怎么写？

参考回答：

可以写成：

```text
基于 Java 21 + Spring Boot 3、React + Tiptap + Yjs、MySQL、Redis 和 WebSocket 实现在线文档协同编辑系统，支持 JWT 鉴权、RBAC 权限、实时同步、版本管理、评论协作和 Prometheus 指标。
```

如果需要突出后端：

```text
负责 Java 后端核心能力开发，设计文档权限模型和 Yjs 增量更新持久化方案，通过 MySQL 事务行锁保证并发写入顺序，并使用 Redis Pub/Sub 支持 WebSocket 多实例广播。
```

追问方向：

- 这些技术分别解决什么问题？
- 哪个模块是你最熟悉的？

## 2. 架构设计类

### Q4：系统整体架构是什么样的？

参考回答：

前端是 React + TypeScript + Tiptap + Yjs，负责编辑器交互、协同状态应用、导入导出和 API 调用。后端有 Java 和 Go 两套实现，但对外暴露同一套 REST 和 WebSocket 契约。Java 后端负责认证、授权、文档元数据、Yjs update 持久化、评论和版本管理。MySQL 是最终状态来源，保存用户、文档、权限、更新序列、版本和评论。Redis 用于 WebSocket 消息跨实例广播。

核心数据流是：用户登录拿到 JWT，打开文档时先通过 REST 获取文档元数据，再建立 WebSocket 连接。后端校验 token 和权限，加载历史 Yjs updates 发给前端。用户编辑时，前端发送 Yjs update，后端写入 MySQL，再广播给其他在线客户端。

追问方向：

- MySQL 和 Redis 分别承担什么角色？
- 前端为什么不直接访问 Redis？
- WebSocket 和 REST 的边界怎么划分？

### Q5：为什么前端要保持后端无关？

参考回答：

因为这个仓库里同时存在 Java 和 Go 两套后端实现，它们是同一产品语义的两种实现，不是两条业务线。如果前端写死某个后端的私有行为，就会导致切换后端时出现兼容问题。因此前端只依赖共享契约，包括 REST、WebSocket 消息格式和 SQL 语义说明。

这样做的好处是接口变更必须先体现在契约上，Java 和 Go 后端都按契约实现，前端不用关心后端语言差异。

追问方向：

- 如果 Java 和 Go 返回字段不一致怎么办？
- 契约文档如何维护？

### Q6：REST 和 WebSocket 在系统中如何分工？

参考回答：

REST 负责确定性业务操作，比如登录、注册、文档列表、创建文档、分享权限、版本保存、评论创建和查询。这些操作需要清晰的请求响应、状态码和权限判断。

WebSocket 负责实时协同消息，比如 `sync:init`、`sync:update`、`presence:update` 和评论变更通知。协同编辑需要低延迟双向通信，所以不适合用轮询 REST 实现。

简单说，REST 负责业务资源状态，WebSocket 负责实时增量同步和在线通知。

追问方向：

- 评论为什么不是完全走 WebSocket？
- 版本恢复后在线用户怎么办？

## 3. 认证与权限类

### Q7：登录认证流程是怎样的？

参考回答：

用户注册时，后端会校验 email、password 和 displayName，然后用 BCrypt 对密码做哈希后写入 MySQL。登录时，根据 email 查询用户和 password_hash，用 BCrypt 验证密码。如果验证通过，后端使用 HMAC-SHA256 签发 JWT，payload 中包含用户 ID、email 和过期时间。

后续 REST 请求通过 `Authorization: Bearer <token>` 传 token，后端解析并校验签名和过期时间，再拿到 userId 执行业务权限判断。

追问方向：

- BCrypt 为什么适合存密码？
- JWT 泄漏怎么办？
- token 过期怎么处理？

### Q8：WebSocket 如何做鉴权？

参考回答：

WebSocket 连接建立时，前端通过子协议传 JWT，例如 `new WebSocket(url, ["bearer", token])`。服务端从 `Sec-WebSocket-Protocol` 里解析 token，校验 JWT 后拿到 userId，再根据 docId 查询用户在该文档中的角色。

如果 token 无效，会返回 `UNAUTHORIZED` 并关闭连接；如果用户没有文档权限，会返回 `FORBIDDEN` 并关闭连接。为了兼容旧客户端，服务端也保留了 query token 的解析方式，但新客户端不应该把 token 放在 URL 中。

追问方向：

- 为什么 token 不推荐放 URL？
- WebSocket 握手后还能不能像 REST 一样每次请求带 header？

### Q9：owner、editor、viewer 三种角色有什么区别？

参考回答：

owner 是文档所有者，拥有完整权限，可以编辑、分享、删除和恢复文档。editor 可以读取和编辑文档，也可以重命名、创建版本、恢复版本、更新评论状态。viewer 只能读取文档和接收协同更新，不能发送编辑 update，也不能修改文档。

这个权限模型同时体现在 REST 和 WebSocket 两侧。比如 REST 的删除文档只允许 owner，WebSocket 的 `sync:update` 只允许 owner 和 editor。

追问方向：

- 为什么 viewer 还能连接 WebSocket？
- 权限校验放前端行不行？

### Q10：如何防止越权访问文档？

参考回答：

后端所有文档相关操作都会基于当前 JWT 中的 userId 查询 `document_permissions`。例如获取文档时会 join `documents` 和 `document_permissions`，确保当前用户对该文档有权限。重命名、删除、分享等操作还会进一步检查角色。

WebSocket 连接阶段也会查询用户角色，并把 role 存到 session attributes 中，后续收到编辑消息时再次判断 `Roles.canEdit(role)`。所以即使前端隐藏按钮被绕过，后端仍然会拒绝越权操作。

追问方向：

- 只在网关层做鉴权够不够？
- session 里的 role 如果中途被修改怎么办？

## 4. 数据库与并发类

### Q11：这个项目有哪些核心表？

参考回答：

核心表包括：

- `users` 保存用户信息和密码哈希。
- `documents` 保存文档标题、所有者、创建更新时间和软删除时间。
- `document_permissions` 保存用户对文档的 owner/editor/viewer 权限。
- `document_updates` 按文档和 seq 保存 Yjs 增量更新。
- `document_versions` 保存某个时间点的 Yjs update 序列快照。
- `document_comments` 和 `document_comment_replies` 保存评论和回复。

表之间通过外键建立关系，比如文档关联 owner，权限关联 document 和 user，update 和 version 都关联 document。

追问方向：

- 为什么权限表用 `(document_id, user_id)` 做主键？
- update 表为什么不用直接存最终 HTML？

### Q12：Yjs update 是怎么持久化的？

参考回答：

服务端不会解析 Yjs update 的语义，而是把它作为二进制增量保存到 MySQL 的 `document_updates` 表。每条 update 绑定一个 document_id 和递增的 seq。客户端打开文档时，服务端按 seq 升序加载所有 updates，通过 `sync:init` 发给前端，前端用 Yjs 依次 apply。

这种 append-only 设计比较适合协同编辑，因为 Yjs 本身负责冲突合并，后端只需要保证 update 不丢、顺序可重放。

追问方向：

- update 越来越多怎么办？
- 为什么不每次保存完整文档内容？

### Q13：多个用户同时编辑时，如何避免 seq 冲突？

参考回答：

Java 后端在 `appendUpdate` 中使用事务，并先对对应文档执行 `SELECT id FROM documents WHERE id = ? FOR UPDATE`。这个行锁会让同一文档的并发写入串行化。然后查询当前最大 seq 加 1，再插入新的 update。表上还有 `(document_id, seq)` 唯一约束，作为数据库层的兜底保护。

所以并发编辑时，同一个文档内 update 会被分配唯一且递增的 seq。

追问方向：

- `FOR UPDATE` 不加事务会怎样？
- 不同文档之间会不会互相阻塞？
- 唯一约束报错后如何处理？

### Q14：为什么 `document_updates` 用 append-only 模型？

参考回答：

因为协同编辑的核心是增量操作。append-only 可以保留每次 Yjs update，客户端重连时可以通过重放 update 恢复状态，服务端也不用理解富文本结构和冲突合并逻辑。

它的代价是 update 数量会增长，所以后续可以做压缩或快照，比如定期把多个 update 合并成一个 Yjs snapshot，再清理旧 update。但在当前项目规模下，按序追加更简单可靠。

追问方向：

- 什么时候需要压缩？
- 压缩时如何保证在线用户不受影响？

### Q15：版本管理是怎么实现的？

参考回答：

版本不是保存普通 HTML，而是保存当前服务器持久化的 Yjs update 序列。创建版本时，后端读取当前文档的所有 updates，Base64 编码后拼成版本快照，写入 `document_versions.state_data`。恢复版本时，后端删除当前文档的 updates，然后按版本快照重建 update 序列。

这样做可以保持协同状态模型一致，恢复后客户端重新打开文档就能加载恢复后的状态。

追问方向：

- 恢复版本时在线用户怎么办？
- 版本数据很大怎么办？

### Q16：软删除是怎么做的？为什么不用物理删除？

参考回答：

文档表里有 `deleted_at` 字段。删除时不删除记录，而是设置 `deleted_at`；列表接口默认只查 active 文档，也可以传 `status=deleted` 查询已删除文档。恢复时把 `deleted_at` 置空。

协同文档涉及权限、更新序列、版本和评论，物理删除容易误删历史数据。软删除可以支持回收站和恢复，也更符合协作产品的使用习惯。

追问方向：

- 已删除文档还能不能编辑？
- 软删除数据越来越多怎么办？

## 5. WebSocket 与协同编辑类

### Q17：WebSocket 连接建立后发生了什么？

参考回答：

连接建立后，服务端先从 URL 中解析 docId，并校验它是否是 UUID。然后解析 WebSocket 子协议里的 JWT，校验 token。接着根据 userId 和 docId 查询用户角色，如果没有权限就拒绝连接。

校验通过后，服务端从 MySQL 加载该文档所有历史 Yjs updates，Base64 编码后通过 `sync:init` 发给客户端。最后把当前 session 加入该文档的本地 session 集合，用于后续广播。

追问方向：

- 为什么连接阶段就要校验权限？
- 历史 update 加载失败怎么办？

### Q18：一次编辑 update 的完整链路是什么？

参考回答：

用户在 Tiptap 编辑器中修改内容后，Yjs 会产生一个 update。前端监听 Y.Doc 的 update 事件，如果 update 不是远端来源且 WebSocket 已连接，就把二进制 update 转成 Base64，通过 `sync:update` 发给服务端。

服务端收到后先判断当前 session 的 role 是否可编辑，然后解码 Base64，检查 update 大小，写入 MySQL。写入成功后构造包含 docId、userId 和 update 的消息，广播给本实例其他客户端，并通过 Redis Pub/Sub 发给其他实例。

其他客户端收到 update 后调用 `Y.applyUpdate` 应用到本地文档。

追问方向：

- 如何避免远端 update 被前端再次发送？
- update 太大为什么要拒绝？

### Q19：为什么 Yjs update 要用 Base64？

参考回答：

Yjs update 是二进制数据，而当前 WebSocket 消息契约使用 JSON 文本。JSON 不能直接安全表达任意二进制，所以前端发送前把 update 转成 Base64，服务端存储前再解码成 byte 数组。服务端发送历史 update 或广播 update 时，也会用 Base64 包装。

追问方向：

- Base64 有什么缺点？
- 能不能直接用二进制 WebSocket 帧？

### Q20：presence 在线状态是怎么实现的？

参考回答：

前端 WebSocket 打开后发送 `presence:update`，包含 displayName 和 color。服务端不持久化 presence，而是补充 docId 和 userId 后广播给当前文档的在线客户端。前端收到后记录用户最后出现时间，并用定时器清理 30 秒内没有更新的用户。

presence 是临时状态，不需要写 MySQL，断线后自然过期即可。

追问方向：

- 多标签页怎么处理？
- 为什么不把在线状态写数据库？

### Q21：断线重连怎么处理？

参考回答：

前端监听 WebSocket close 和 error 事件，进入 offline 状态后按指数退避重连，最多重试 12 次。对于 `UNAUTHORIZED`、`FORBIDDEN`、`INVALID_DOCUMENT_ID` 这类不可恢复错误，前端不会继续重连。

重连成功后，服务端会再次发送 `sync:init`，前端应用服务器端持久化的历史 updates，从而恢复到服务端状态。

追问方向：

- 重连期间用户继续编辑怎么办？
- 如何做离线编辑？

### Q22：viewer 为什么不能发送编辑 update？

参考回答：

viewer 角色的语义是只读。它可以连接 WebSocket 接收 `sync:init` 和其他用户的 `sync:update`，这样能实时看到文档变化。但如果 viewer 发送 `sync:update`，服务端会检查 role，发现不可编辑后返回 `FORBIDDEN`。

这保证了权限不是只靠前端 editable 控制，而是在服务端实时同步链路中也严格执行。

追问方向：

- 前端 readOnly 和后端权限有什么关系？
- 如果 viewer 构造 WebSocket 消息怎么办？

## 6. Redis 与扩展性类

### Q23：Redis 在项目里用来做什么？

参考回答：

Redis 主要用于 WebSocket 消息跨实例广播。单个 Java 实例只能直接给自己内存中的 WebSocket session 发消息。如果后端部署多个实例，用户 A 连接实例 1，用户 B 连接实例 2，单靠本地内存广播就无法互相收到消息。

所以服务端本地广播后，还会把消息发布到 Redis 的 `doc:{docId}` 频道。其他实例订阅 `doc:*`，收到消息后再广播给自己本地连接的客户端。

追问方向：

- Redis Pub/Sub 能保证消息不丢吗？
- 为什么还要写 MySQL？

### Q24：Redis Pub/Sub 丢消息怎么办？

参考回答：

在这个设计里，Redis Pub/Sub 只是实时通知通道，不是最终状态来源。真正的协同 update 会先写入 MySQL。即使某个客户端因为网络或 Redis 问题没收到实时广播，断线重连或重新打开文档时，服务端会从 MySQL 加载完整 update 序列，通过 `sync:init` 恢复状态。

所以系统把可靠性放在 MySQL 持久化上，把低延迟分发放在 Redis Pub/Sub 上。

追问方向：

- 如果需要更强消息可靠性怎么做？
- Redis Stream 是否更合适？

### Q25：如何避免实例消费自己发布的 Redis 消息？

参考回答：

每个 RedisBus 初始化时会生成一个 source id。发布消息时把 source、docId 和 body 包进 envelope。订阅端收到消息后，如果 envelope 的 source 和本实例 source 相同，就直接忽略。这样可以避免本实例先本地广播一次，又从 Redis 收到自己发布的消息后重复广播。

追问方向：

- source id 重启后会变吗？
- 重复消息客户端能否容忍？

## 7. 前端全栈类

### Q26：前端编辑器是怎么和后端协同的？

参考回答：

前端使用 Tiptap 作为富文本编辑器，使用 Yjs Collaboration 扩展作为协同状态层。组件创建 Y.Doc 后，将它传给 Tiptap 的 Collaboration 扩展。WebSocket 收到服务端 update 时，前端调用 `Y.applyUpdate(ydoc, update, "remote")` 应用远端变化。本地编辑产生 update 时，如果 origin 不是 remote，就通过 WebSocket 发给服务端。

这样编辑器 UI、协同算法和网络同步是分层的：Tiptap 负责编辑体验，Yjs 负责冲突合并，WebSocket 负责传输。

追问方向：

- Tiptap 和 Yjs 分别解决什么问题？
- origin 为什么要标记为 remote？

### Q27：导入导出为什么放在前端边界？

参考回答：

这个项目的后端保持格式无关，只持久化 Yjs 协同状态。Markdown、HTML、TXT 导入导出以及 PDF 打印都属于前端展示和格式转换问题。放在前端可以避免 Java 和 Go 后端各自实现一套格式转换逻辑，也能保持后端契约稳定。

导入时前端先预览和清洗，用户确认后再创建新文档并把内容进入编辑器初始化路径。导出时从当前编辑器 HTML 转成目标格式。

追问方向：

- PDF 导出为什么是浏览器 print flow？
- HTML 导入如何防 XSS？

### Q28：前端如何做环境切换？

参考回答：

前端通过配置区分 API_BASE 和 WS_BASE，环境切换由 `.env.local` 或 Vite mode 控制。这样前端源码不写死 Java 或 Go 后端地址，运行时可以选择连接 Java 后端或 Go 后端。

追问方向：

- 为什么不在代码里 if Java else Go？
- 本地和 Docker 端口不同怎么处理？

## 8. 测试与工程能力类

### Q29：项目里有哪些测试？

参考回答：

Java 后端有认证、JWT、角色权限、WebSocket handler、指标等测试；前端有 API、配置、文档格式转换、导出样式、模板和协同编辑相关测试。Go 后端也有认证、配置、角色、WebSocket、指标和 JSON 测试。

测试重点不是追求数量，而是覆盖容易出问题的契约边界，比如权限判断、错误格式、WebSocket 消息、重连策略、导入导出格式转换。

追问方向：

- WebSocket 怎么测？
- 前后端契约怎么测？

### Q30：如何验证这个项目？

参考回答：

常用命令包括：

```powershell
npm run test:web
npm run build:web
npm run test:java
npm run test:go
```

本地运行时可以用 Docker Compose 启动 Web、Java API、MySQL 和 Redis，也可以分别启动前端、Java 后端、Go 后端。指标方面 Java 和 Go 后端都暴露 `/metrics`，返回 Prometheus 文本格式。

追问方向：

- 构建和测试分别覆盖什么？
- Docker Compose 里有哪些服务？

### Q31：Prometheus 指标有什么用？

参考回答：

指标用于观察系统运行状态。项目中暴露了 HTTP 请求数、WebSocket 总连接数、活跃连接数和 WebSocket 消息数。这样可以在压测或线上运行时观察请求量、连接量和消息量，帮助定位流量波动、连接泄漏或消息异常。

追问方向：

- 还可以补充哪些指标？
- 如何定位 WebSocket 消息堆积？

## 9. 系统设计提升类

### Q32：如果 update 数量越来越多，打开文档变慢怎么办？

参考回答：

可以引入快照压缩机制。当前设计是加载并重放所有 Yjs updates，简单可靠，但长期运行后 update 数量会变大。优化方案是定期把一批 updates 合并成一个 compacted update 或 snapshot，保存为新的基线状态，再清理旧增量。客户端打开文档时先加载快照，再加载快照后的增量。

实现时要注意和在线编辑并发的关系，可以在事务中锁定文档，记录压缩边界 seq，确保压缩期间新增 update 不丢失。

追问方向：

- 压缩任务怎么触发？
- 压缩失败如何回滚？

### Q33：如果并发用户很多，系统瓶颈在哪里？

参考回答：

主要瓶颈可能有四类。第一是 WebSocket 连接数，单实例内存和线程模型会受压力。第二是同一文档的 update 写入，因为当前同文档写入通过行锁串行化。第三是打开大文档时加载历史 updates 的耗时。第四是 Redis Pub/Sub 广播量。

优化方向包括 WebSocket 水平扩容、引入 update 快照压缩、热点文档分片或队列化写入、限制单次 update 大小、增加指标监控和压测。

追问方向：

- 同一篇热点文档如何优化？
- 多实例下 session 怎么管理？

### Q34：为什么不直接把文档最终 HTML 存到数据库？

参考回答：

如果只存最终 HTML，后端需要处理多人同时编辑时的冲突合并，这会非常复杂。Yjs 的优势是用 CRDT 模型处理并发编辑冲突，服务端只需要保存和转发 update。最终 HTML 更适合导出或展示，不适合作为协同编辑的源状态。

不过可以额外维护一份只读预览 HTML，用于搜索或快速展示，但它不应该替代 Yjs 状态。

追问方向：

- 搜索文档内容怎么做？
- 后端是否需要理解富文本结构？

### Q35：如何保证 Java 和 Go 后端行为一致？

参考回答：

核心是共享契约优先。REST 接口字段、错误格式、WebSocket 消息类型、SQL schema 和角色语义都放在 `packages/shared-contract` 和 `docs` 中。新增或修改能力时，先更新契约，再分别检查 Java 和 Go 实现，最后运行对应测试。

如果发现两端行为不一致，应该以共享契约为准，而不是让前端适配某一端特例。

追问方向：

- 契约变更流程是什么？
- 如何避免文档落后于实现？

## 10. 安全类

### Q36：这个项目有哪些安全设计？

参考回答：

主要包括：密码使用 BCrypt 哈希存储；REST 使用 Bearer JWT 鉴权；JWT 设置过期时间；WebSocket 通过子协议传 token，避免 token 出现在 URL；所有文档操作都做服务端权限校验；viewer 不能发送编辑 update；WebSocket update 有大小限制；错误响应使用统一结构，避免泄漏过多内部细节。

追问方向：

- CORS 和 WebSocket Origin 怎么控制？
- JWT secret 如何管理？

### Q37：如果 token 被盗怎么办？

参考回答：

短期内攻击者可能使用 token 访问接口，所以 token 必须设置合理 TTL，并且生产环境要使用 HTTPS，避免传输中泄漏。WebSocket 不把 token 放 URL，也是在减少日志和浏览器历史泄漏风险。

进一步可以做 refresh token、服务端 token denylist、用户主动退出失效、敏感操作二次校验等。

追问方向：

- JWT 无状态和主动失效的矛盾怎么解决？
- refresh token 怎么设计？

## 11. 行为面试类

### Q38：这个项目中你遇到的最大技术难点是什么？

参考回答：

最大难点是实时协同编辑链路的边界划分。协同编辑涉及前端编辑器、Yjs 状态、WebSocket 传输、MySQL 持久化和 Redis 广播。如果边界不清晰，后端可能会承担不该承担的富文本合并逻辑，或者前端依赖某个后端实现细节。

最终设计是让 Yjs 负责冲突合并，前端负责编辑体验和格式转换，Java 后端负责鉴权、权限、update 持久化和广播，MySQL 作为状态来源，Redis 只做实时通知。这样每一层职责比较清楚。

追问方向：

- 你怎么验证这个方案是可行的？
- 如果重新做一次会优化什么？

### Q39：你怎么和前端或其他后端协作？

参考回答：

这个项目的协作核心是契约先行。因为同一个前端要适配 Java 和 Go 两套后端，所以接口字段、错误格式、WebSocket 消息和角色语义不能各写各的。开发时我会先看共享契约和文档，再改对应实现。如果改到 API、WebSocket 或 schema，也要同步更新契约文档，并检查另一套后端是否需要同步。

追问方向：

- 如果对契约设计有分歧怎么办？
- 怎么处理兼容旧客户端？

### Q40：如果面试官让你现场改进这个项目，你会优先做什么？

参考回答：

我会优先做三件事。第一是 Yjs update 快照压缩，解决大文档打开慢和 update 表增长问题。第二是补充更完整的契约测试，确保 Java 和 Go 后端行为一致。第三是增强可观测性，例如增加 WebSocket 错误数、广播耗时、Redis 发布失败数、文档 update 写入耗时等指标。

这些改进都围绕系统长期运行的可靠性和可维护性，而不是单纯增加功能。

追问方向：

- 快照压缩怎么兼容历史版本？
- 哪个指标最能反映协同链路健康？

## 12. 简历答辩模板

### 30 秒版本

```text
我做的是一个在线文档协同编辑系统，前端用 React、Tiptap 和 Yjs，后端用 Java 21、Spring Boot、MySQL、Redis 和 WebSocket。系统支持登录鉴权、文档权限、实时协同、在线状态、版本、评论和导入导出。我主要关注 Java 后端的认证授权、Yjs update 持久化、WebSocket 同步和 Redis 跨实例广播，同时保证 Java 和 Go 两套后端遵循同一套契约。
```

### 2 分钟版本

```text
这个项目是在线文档协同编辑系统，业务上类似多人同时编辑同一份富文本文档。前端使用 React + TypeScript + Tiptap + Yjs，Java 后端使用 Spring Boot 3，数据层使用 MySQL，实时广播使用 WebSocket 和 Redis Pub/Sub。

用户登录后拿到 JWT，打开文档时后端会校验 token 和用户在文档中的角色。服务端从 MySQL 加载该文档历史 Yjs updates，通过 sync:init 初始化客户端。用户编辑时，前端产生 Yjs update，通过 WebSocket 发给后端。后端判断 owner/editor 权限，使用事务和行锁按 seq 追加写入 document_updates，然后广播给本地 WebSocket session，并通过 Redis 发给其他实例。

项目还实现了 owner/editor/viewer 权限、文档分享、软删除恢复、版本快照、评论协作、导入导出和 Prometheus 指标。架构上前端只依赖共享 REST 和 WebSocket 契约，因此可以同时适配 Java 和 Go 两套后端。
```

### 项目亮点版本

```text
这个项目的亮点主要有三个。第一是实时协同链路，使用 Yjs 处理并发编辑冲突，后端只负责 update 持久化和广播，降低了服务端复杂度。第二是数据一致性，同一文档的 update 通过 MySQL 事务和行锁生成递增 seq，并用唯一约束兜底。第三是可扩展性，单实例内使用 WebSocket 本地广播，多实例通过 Redis Pub/Sub 转发，同时 MySQL 作为最终状态来源，客户端断线后可以通过 sync:init 恢复。
```

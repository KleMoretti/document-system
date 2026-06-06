# Java 全栈简历问答

本文基于本仓库的在线文档协同编辑系统生成，用于准备 Java 全栈简历项目追问。回答时建议坚持一个原则：先讲业务目标，再讲技术方案，最后讲权衡和结果。

## 1. 项目介绍类

### Q1：请介绍一下你简历上的在线文档协同编辑系统。

参考回答：

这是一个在线文档协同编辑系统，核心目标是让多个用户可以在浏览器里共同编辑同一篇富文本文档。前端使用 React、TypeScript、Tiptap 和 Yjs，Java 后端使用 Spring Boot 3、JdbcTemplate、MySQL、Redis 和 WebSocket。

系统支持用户注册登录、JWT 鉴权、文档创建和分享、owner/editor/viewer 权限控制、实时协同编辑、在线状态、版本保存和恢复、评论协作、软删除恢复、导入导出以及 Prometheus 指标。架构上前端只依赖共享 REST 和 WebSocket 契约，因此同一个前端可以切换 Java 或 Go 后端。

追问参考回答：

#### Q1-1：你主要负责哪一块？

参考回答：我主要负责 Java 后端核心链路，包括认证授权、文档 REST API、权限控制、WebSocket 协同同步、Yjs update 持久化、Redis 跨实例广播，以及相关测试和契约对齐。

#### Q1-2：为什么要做 Java 和 Go 两套后端？

参考回答：它们是同一产品语义的两种技术实现，用来验证前端只依赖共享契约而不是依赖某个后端私有行为。这样可以练习契约驱动开发，也能对比不同语言在同一业务能力上的实现方式。

#### Q1-3：项目里最难的点是什么？

参考回答：最难的是协同编辑链路的边界划分。Yjs 负责冲突合并，前端负责编辑体验，后端负责鉴权、持久化和广播，MySQL 做状态来源，Redis 只做实时分发。边界清楚后，系统才容易维护和扩展。

### Q2：这个项目最能体现你 Java 能力的地方是什么？

参考回答：

我认为主要体现在三块。第一是 Spring Boot REST API 的完整业务建模，包括认证、文档、权限、版本和评论。第二是协同编辑 update 的持久化设计，服务端用事务和行锁保证同一文档下 seq 按顺序写入，避免并发编辑导致序号冲突。第三是 WebSocket 实时同步通道，连接时做 JWT 和文档权限校验，初始化时加载历史 Yjs update，编辑时持久化并广播。

这些能力不是单纯 CRUD，而是涉及权限、安全、并发、一致性、实时通信和可观测性。

追问参考回答：

#### Q2-1：为什么不用 JPA？

参考回答：这个项目的数据访问以明确 SQL、事务、行锁和契约一致性为主，JdbcTemplate 更直观，能直接控制 `SELECT ... FOR UPDATE`、join 查询和批量读取。JPA 更适合领域对象关系比较稳定的场景，但这里很多逻辑和协同 update 序列有关，直接 SQL 可控性更强。

#### Q2-2：`@Transactional` 用在了哪里？

参考回答：主要用在创建文档、追加 Yjs update、恢复版本等需要多条 SQL 保持一致的操作中。例如追加 update 时要锁定文档、计算 seq、插入 update、更新文档更新时间，这些步骤必须在同一个事务里完成。

#### Q2-3：行锁具体锁的是什么？

参考回答：锁的是 `documents` 表中当前 `docId` 对应的那一行。这样同一篇文档的并发 update 写入会串行化，不同文档之间通常不会互相阻塞。

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

追问参考回答：

#### Q3-1：这些技术分别解决什么问题？

参考回答：React 和 Tiptap 解决前端富文本编辑体验，Yjs 解决多人编辑冲突合并，Spring Boot 提供后端 API 和 WebSocket 能力，MySQL 保存最终状态，Redis 做跨实例实时广播，JWT 和 RBAC 解决认证授权，Docker Compose 解决本地环境编排。

#### Q3-2：哪个模块是你最熟悉的？

参考回答：我最熟悉 Java 后端的协同同步链路，包含 WebSocket 连接鉴权、Yjs update 持久化、权限校验、本地广播和 Redis Pub/Sub 跨实例广播。

## 2. 架构设计类

### Q4：系统整体架构是什么样的？

参考回答：

前端是 React + TypeScript + Tiptap + Yjs，负责编辑器交互、协同状态应用、导入导出和 API 调用。后端有 Java 和 Go 两套实现，但对外暴露同一套 REST 和 WebSocket 契约。Java 后端负责认证、授权、文档元数据、Yjs update 持久化、评论和版本管理。MySQL 是最终状态来源，保存用户、文档、权限、更新序列、版本和评论。Redis 用于 WebSocket 消息跨实例广播。

核心数据流是：用户登录拿到 JWT，打开文档时先通过 REST 获取文档元数据，再建立 WebSocket 连接。后端校验 token 和权限，加载历史 Yjs updates 发给前端。用户编辑时，前端发送 Yjs update，后端写入 MySQL，再广播给其他在线客户端。

追问参考回答：

#### Q4-1：MySQL 和 Redis 分别承担什么角色？

参考回答：MySQL 是最终状态来源，保存用户、文档、权限、更新序列、版本和评论。Redis 只是实时广播通道，用来把一个实例收到的 WebSocket 消息转发到其他实例。

#### Q4-2：前端为什么不直接访问 Redis？

参考回答：Redis 属于后端基础设施，不能暴露给浏览器。前端直接访问 Redis 会带来鉴权、数据隔离和安全问题，也会破坏后端对业务权限的统一控制。

#### Q4-3：WebSocket 和 REST 的边界怎么划分？

参考回答：REST 负责资源型业务操作，比如登录、文档列表、分享、版本和评论；WebSocket 负责实时增量消息，比如协同 update、在线状态和评论变更通知。

### Q5：为什么前端要保持后端无关？

参考回答：

因为这个仓库里同时存在 Java 和 Go 两套后端实现，它们是同一产品语义的两种实现，不是两条业务线。如果前端写死某个后端的私有行为，就会导致切换后端时出现兼容问题。因此前端只依赖共享契约，包括 REST、WebSocket 消息格式和 SQL 语义说明。

这样做的好处是接口变更必须先体现在契约上，Java 和 Go 后端都按契约实现，前端不用关心后端语言差异。

追问参考回答：

#### Q5-1：如果 Java 和 Go 返回字段不一致怎么办？

参考回答：以共享契约为准，先确认 `openapi.yml`、`websocket.md` 或相关文档中定义的字段，再修正偏离契约的一端。不能让前端为某个后端写特例。

#### Q5-2：契约文档如何维护？

参考回答：新增或修改 REST、WebSocket、角色语义、错误格式、SQL schema 时，先更新 `packages/shared-contract` 和 `docs`，再同步检查 Java、Go 和前端实现，最后跑对应测试。

### Q6：REST 和 WebSocket 在系统中如何分工？

参考回答：

REST 负责确定性业务操作，比如登录、注册、文档列表、创建文档、分享权限、版本保存、评论创建和查询。这些操作需要清晰的请求响应、状态码和权限判断。

WebSocket 负责实时协同消息，比如 `sync:init`、`sync:update`、`presence:update` 和评论变更通知。协同编辑需要低延迟双向通信，所以不适合用轮询 REST 实现。

简单说，REST 负责业务资源状态，WebSocket 负责实时增量同步和在线通知。

追问参考回答：

#### Q6-1：评论为什么不是完全走 WebSocket？

参考回答：评论是可查询、可修改、可回复、可恢复的业务资源，必须有可靠持久化和明确请求响应，所以主体走 REST。WebSocket 只广播 `comment:created`、`comment:updated`、`comment:resolved` 这类轻量通知，让客户端刷新。

#### Q6-2：版本恢复后在线用户怎么办？

参考回答：当前契约说明恢复版本会替换服务端持久化 update 序列，在线用户应重新打开文档加载恢复后的状态。更完整的方案是恢复后广播一个强制 reload 或 state-reset 通知。

## 3. 认证与权限类

### Q7：登录认证流程是怎样的？

参考回答：

用户注册时，后端会校验 email、password 和 displayName，然后用 BCrypt 对密码做哈希后写入 MySQL。登录时，根据 email 查询用户和 password_hash，用 BCrypt 验证密码。如果验证通过，后端使用 HMAC-SHA256 签发 JWT，payload 中包含用户 ID、email 和过期时间。

后续 REST 请求通过 `Authorization: Bearer <token>` 传 token，后端解析并校验签名和过期时间，再拿到 userId 执行业务权限判断。

追问参考回答：

#### Q7-1：BCrypt 为什么适合存密码？

参考回答：BCrypt 自带盐并且计算成本可调，比普通哈希更能抵抗彩虹表和暴力破解。即使数据库泄漏，攻击者也很难快速还原明文密码。

#### Q7-2：JWT 泄漏怎么办？

参考回答：首先要用 HTTPS、避免 token 进入 URL、设置较短 TTL。进一步可以引入 refresh token、服务端 denylist、主动退出失效和敏感操作二次校验。

#### Q7-3：token 过期怎么处理？

参考回答：当前后端校验 `exp`，过期后返回未授权错误，前端应引导重新登录。生产系统一般会增加 refresh token，在访问 token 过期时尝试刷新。

### Q8：WebSocket 如何做鉴权？

参考回答：

WebSocket 连接建立时，前端通过子协议传 JWT，例如 `new WebSocket(url, ["bearer", token])`。服务端从 `Sec-WebSocket-Protocol` 里解析 token，校验 JWT 后拿到 userId，再根据 docId 查询用户在该文档中的角色。

如果 token 无效，会返回 `UNAUTHORIZED` 并关闭连接；如果用户没有文档权限，会返回 `FORBIDDEN` 并关闭连接。为了兼容旧客户端，服务端也保留了 query token 的解析方式，但新客户端不应该把 token 放在 URL 中。

追问参考回答：

#### Q8-1：为什么 token 不推荐放 URL？

参考回答：URL 可能出现在浏览器历史、代理日志、访问日志和 Referer 中，泄漏风险更高。WebSocket 用子协议传 token 能减少这些暴露面。

#### Q8-2：WebSocket 握手后还能不能像 REST 一样每次请求带 header？

参考回答：浏览器 WebSocket API 在连接建立后不能像 REST 那样给每条消息加 HTTP header，所以通常在握手阶段完成认证，然后把 userId 和 role 存在 session 上，后续消息再做业务权限校验。

### Q9：owner、editor、viewer 三种角色有什么区别？

参考回答：

owner 是文档所有者，拥有完整权限，可以编辑、分享、删除和恢复文档。editor 可以读取和编辑文档，也可以重命名、创建版本、恢复版本、更新评论状态。viewer 只能读取文档和接收协同更新，不能发送编辑 update，也不能修改文档。

这个权限模型同时体现在 REST 和 WebSocket 两侧。比如 REST 的删除文档只允许 owner，WebSocket 的 `sync:update` 只允许 owner 和 editor。

追问参考回答：

#### Q9-1：为什么 viewer 还能连接 WebSocket？

参考回答：viewer 虽然不能编辑，但需要实时看到其他人的编辑结果，所以可以连接 WebSocket 接收 `sync:init` 和 `sync:update`。只是当它发送编辑 update 时，服务端会拒绝。

#### Q9-2：权限校验放前端行不行？

参考回答：不行。前端限制只能改善用户体验，不能作为安全边界。用户可以绕过前端直接构造 HTTP 或 WebSocket 消息，所以权限必须在后端校验。

### Q10：如何防止越权访问文档？

参考回答：

后端所有文档相关操作都会基于当前 JWT 中的 userId 查询 `document_permissions`。例如获取文档时会 join `documents` 和 `document_permissions`，确保当前用户对该文档有权限。重命名、删除、分享等操作还会进一步检查角色。

WebSocket 连接阶段也会查询用户角色，并把 role 存到 session attributes 中，后续收到编辑消息时再次判断 `Roles.canEdit(role)`。所以即使前端隐藏按钮被绕过，后端仍然会拒绝越权操作。

追问参考回答：

#### Q10-1：只在网关层做鉴权够不够？

参考回答：不够。网关通常只能判断 token 是否有效，但文档权限是资源级授权，需要知道 userId 对 docId 的角色，因此必须在业务服务中再次校验。

#### Q10-2：session 里的 role 如果中途被修改怎么办？

参考回答：当前连接阶段缓存 role，适合简单场景。如果需要权限实时生效，可以在每次 `sync:update` 时重新查询角色，或者在权限变更时广播断开/降权通知，让客户端重新连接。

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

追问参考回答：

#### Q11-1：为什么权限表用 `(document_id, user_id)` 做主键？

参考回答：同一个用户对同一篇文档只能有一个角色，用复合主键可以天然避免重复授权记录，也方便通过 `ON DUPLICATE KEY UPDATE` 更新角色。

#### Q11-2：update 表为什么不用直接存最终 HTML？

参考回答：最终 HTML 无法表达并发编辑的增量冲突过程。Yjs update 是协同状态的增量，由 Yjs 负责合并，服务端保存 update 可以重放恢复状态。

### Q12：Yjs update 是怎么持久化的？

参考回答：

服务端不会解析 Yjs update 的语义，而是把它作为二进制增量保存到 MySQL 的 `document_updates` 表。每条 update 绑定一个 document_id 和递增的 seq。客户端打开文档时，服务端按 seq 升序加载所有 updates，通过 `sync:init` 发给前端，前端用 Yjs 依次 apply。

这种 append-only 设计比较适合协同编辑，因为 Yjs 本身负责冲突合并，后端只需要保证 update 不丢、顺序可重放。

追问参考回答：

#### Q12-1：update 越来越多怎么办？

参考回答：可以做快照压缩。定期把已有 update 合并成一个基线快照，只保留快照之后的增量。打开文档时先加载快照，再加载后续 update。

#### Q12-2：为什么不每次保存完整文档内容？

参考回答：完整内容写入成本高，而且多人并发时需要服务端解决覆盖和冲突问题。保存 Yjs update 更符合 CRDT 协同模型，后端复杂度更低。

### Q13：多个用户同时编辑时，如何避免 seq 冲突？

参考回答：

Java 后端在 `appendUpdate` 中使用事务，并先对对应文档执行 `SELECT id FROM documents WHERE id = ? FOR UPDATE`。这个行锁会让同一文档的并发写入串行化。然后查询当前最大 seq 加 1，再插入新的 update。表上还有 `(document_id, seq)` 唯一约束，作为数据库层的兜底保护。

所以并发编辑时，同一个文档内 update 会被分配唯一且递增的 seq。

追问参考回答：

#### Q13-1：`FOR UPDATE` 不加事务会怎样？

参考回答：行锁依赖事务边界。如果不在事务中，锁可能很快释放，无法覆盖后续计算 seq 和插入 update 的步骤，就不能保证并发安全。

#### Q13-2：不同文档之间会不会互相阻塞？

参考回答：正常不会。行锁锁的是指定 docId 的文档行，不同文档对应不同记录，可以并发写入。

#### Q13-3：唯一约束报错后如何处理？

参考回答：唯一约束是兜底保护。生产里可以捕获冲突异常后重试整个 appendUpdate 流程，重新获取锁、重新计算 seq、再插入。

### Q14：为什么 `document_updates` 用 append-only 模型？

参考回答：

因为协同编辑的核心是增量操作。append-only 可以保留每次 Yjs update，客户端重连时可以通过重放 update 恢复状态，服务端也不用理解富文本结构和冲突合并逻辑。

它的代价是 update 数量会增长，所以后续可以做压缩或快照，比如定期把多个 update 合并成一个 Yjs snapshot，再清理旧 update。但在当前项目规模下，按序追加更简单可靠。

追问参考回答：

#### Q14-1：什么时候需要压缩？

参考回答：当单篇文档 update 数量很大、打开文档初始化明显变慢、数据库存储增长明显或网络传输变大时，就需要引入压缩。

#### Q14-2：压缩时如何保证在线用户不受影响？

参考回答：压缩要记录明确的 seq 边界，在事务中处理边界内的 update。边界之后的新 update 继续追加，客户端可以通过重新初始化或版本号检测切换到压缩后的状态。

### Q15：版本管理是怎么实现的？

参考回答：

版本不是保存普通 HTML，而是保存当前服务器持久化的 Yjs update 序列。创建版本时，后端读取当前文档的所有 updates，Base64 编码后拼成版本快照，写入 `document_versions.state_data`。恢复版本时，后端删除当前文档的 updates，然后按版本快照重建 update 序列。

这样做可以保持协同状态模型一致，恢复后客户端重新打开文档就能加载恢复后的状态。

追问参考回答：

#### Q15-1：恢复版本时在线用户怎么办？

参考回答：当前设计建议在线客户端重新打开文档。更完善的做法是恢复成功后广播一个 `document:restored` 或 `sync:reset` 通知，客户端清空本地状态后重新加载服务端 update。

#### Q15-2：版本数据很大怎么办？

参考回答：可以配合快照压缩、限制版本数量、按时间清理旧版本，或者把版本状态存到对象存储中，数据库只保留元数据和引用地址。

### Q16：软删除是怎么做的？为什么不用物理删除？

参考回答：

文档表里有 `deleted_at` 字段。删除时不删除记录，而是设置 `deleted_at`；列表接口默认只查 active 文档，也可以传 `status=deleted` 查询已删除文档。恢复时把 `deleted_at` 置空。

协同文档涉及权限、更新序列、版本和评论，物理删除容易误删历史数据。软删除可以支持回收站和恢复，也更符合协作产品的使用习惯。

追问参考回答：

#### Q16-1：已删除文档还能不能编辑？

参考回答：不能。常规查询会过滤 `deleted_at IS NULL`，获取文档和编辑前都应校验文档未删除。恢复后才允许继续编辑。

#### Q16-2：软删除数据越来越多怎么办？

参考回答：可以增加保留期策略，例如删除超过 30 或 90 天后进入归档或物理清理，同时保留审计日志，避免误删后无法恢复。

## 5. WebSocket 与协同编辑类

### Q17：WebSocket 连接建立后发生了什么？

参考回答：

连接建立后，服务端先从 URL 中解析 docId，并校验它是否是 UUID。然后解析 WebSocket 子协议里的 JWT，校验 token。接着根据 userId 和 docId 查询用户角色，如果没有权限就拒绝连接。

校验通过后，服务端从 MySQL 加载该文档所有历史 Yjs updates，Base64 编码后通过 `sync:init` 发给客户端。最后把当前 session 加入该文档的本地 session 集合，用于后续广播。

追问参考回答：

#### Q17-1：为什么连接阶段就要校验权限？

参考回答：因为 WebSocket 是长连接，如果连接时不校验，无权限用户可能拿到历史 update 或占用连接资源。连接阶段校验可以尽早拒绝非法访问。

#### Q17-2：历史 update 加载失败怎么办？

参考回答：服务端返回 `SYNC_INIT_FAILED` 并关闭连接。客户端可以展示离线或失败状态，避免在未初始化完整状态时继续编辑。

### Q18：一次编辑 update 的完整链路是什么？

参考回答：

用户在 Tiptap 编辑器中修改内容后，Yjs 会产生一个 update。前端监听 Y.Doc 的 update 事件，如果 update 不是远端来源且 WebSocket 已连接，就把二进制 update 转成 Base64，通过 `sync:update` 发给服务端。

服务端收到后先判断当前 session 的 role 是否可编辑，然后解码 Base64，检查 update 大小，写入 MySQL。写入成功后构造包含 docId、userId 和 update 的消息，广播给本实例其他客户端，并通过 Redis Pub/Sub 发给其他实例。

其他客户端收到 update 后调用 `Y.applyUpdate` 应用到本地文档。

追问参考回答：

#### Q18-1：如何避免远端 update 被前端再次发送？

参考回答：前端应用远端 update 时传入 origin 标记为 `"remote"`，监听 Y.Doc update 时如果发现 origin 是 remote，就不再通过 WebSocket 发送。

#### Q18-2：update 太大为什么要拒绝？

参考回答：过大的 update 可能导致内存、数据库和网络压力，也可能被恶意利用做 DoS。限制单条 update 大小可以保护服务端资源。

### Q19：为什么 Yjs update 要用 Base64？

参考回答：

Yjs update 是二进制数据，而当前 WebSocket 消息契约使用 JSON 文本。JSON 不能直接安全表达任意二进制，所以前端发送前把 update 转成 Base64，服务端存储前再解码成 byte 数组。服务端发送历史 update 或广播 update 时，也会用 Base64 包装。

追问参考回答：

#### Q19-1：Base64 有什么缺点？

参考回答：Base64 会让数据体积大约增加三分之一，还需要编码解码成本。优点是能安全放进 JSON 文本消息，兼容性好。

#### Q19-2：能不能直接用二进制 WebSocket 帧？

参考回答：可以。二进制帧效率更高，但契约和前后端处理会更复杂。当前项目选择 JSON + Base64 是为了消息结构清晰、调试方便。

### Q20：presence 在线状态是怎么实现的？

参考回答：

前端 WebSocket 打开后发送 `presence:update`，包含 displayName 和 color。服务端不持久化 presence，而是补充 docId 和 userId 后广播给当前文档的在线客户端。前端收到后记录用户最后出现时间，并用定时器清理 30 秒内没有更新的用户。

presence 是临时状态，不需要写 MySQL，断线后自然过期即可。

追问参考回答：

#### Q20-1：多标签页怎么处理？

参考回答：当前用 displayName 作为在线展示 key，多个标签可能被合并。更精确的做法是为每个连接生成 sessionId，以 sessionId 维度记录在线状态，再按用户聚合展示。

#### Q20-2：为什么不把在线状态写数据库？

参考回答：在线状态是高频、短生命周期的临时状态，写数据库成本高且容易产生脏数据。用内存广播和超时清理更合适。

### Q21：断线重连怎么处理？

参考回答：

前端监听 WebSocket close 和 error 事件，进入 offline 状态后按指数退避重连，最多重试 12 次。对于 `UNAUTHORIZED`、`FORBIDDEN`、`INVALID_DOCUMENT_ID` 这类不可恢复错误，前端不会继续重连。

重连成功后，服务端会再次发送 `sync:init`，前端应用服务器端持久化的历史 updates，从而恢复到服务端状态。

追问参考回答：

#### Q21-1：重连期间用户继续编辑怎么办？

参考回答：当前设计更偏在线编辑，断线后状态会进入 offline，未实现完整离线队列。生产优化可以缓存本地 update，重连后再基于服务端状态做同步，但要处理冲突和重复提交。

#### Q21-2：如何做离线编辑？

参考回答：可以把 Y.Doc 持久化到 IndexedDB，本地继续编辑并保存 update。网络恢复后和服务端同步，由 Yjs 合并状态，同时要处理权限变化和版本恢复这类服务端事件。

### Q22：viewer 为什么不能发送编辑 update？

参考回答：

viewer 角色的语义是只读。它可以连接 WebSocket 接收 `sync:init` 和其他用户的 `sync:update`，这样能实时看到文档变化。但如果 viewer 发送 `sync:update`，服务端会检查 role，发现不可编辑后返回 `FORBIDDEN`。

这保证了权限不是只靠前端 editable 控制，而是在服务端实时同步链路中也严格执行。

追问参考回答：

#### Q22-1：前端 readOnly 和后端权限有什么关系？

参考回答：前端 readOnly 是用户体验层，控制编辑器是否可编辑；后端权限是安全边界，真正决定是否接受 `sync:update`。两者要一致，但以后端为准。

#### Q22-2：如果 viewer 构造 WebSocket 消息怎么办？

参考回答：服务端收到 `sync:update` 会检查 session 中的 role，viewer 不满足 `canEdit`，会返回 `FORBIDDEN`，不会写入数据库或广播。

## 6. Redis 与扩展性类

### Q23：Redis 在项目里用来做什么？

参考回答：

Redis 主要用于 WebSocket 消息跨实例广播。单个 Java 实例只能直接给自己内存中的 WebSocket session 发消息。如果后端部署多个实例，用户 A 连接实例 1，用户 B 连接实例 2，单靠本地内存广播就无法互相收到消息。

所以服务端本地广播后，还会把消息发布到 Redis 的 `doc:{docId}` 频道。其他实例订阅 `doc:*`，收到消息后再广播给自己本地连接的客户端。

追问参考回答：

#### Q23-1：Redis Pub/Sub 能保证消息不丢吗？

参考回答：不能。Pub/Sub 是实时分发模型，订阅者离线或网络异常时消息可能丢失。所以项目把可靠状态放在 MySQL，Redis 只做实时通知。

#### Q23-2：为什么还要写 MySQL？

参考回答：WebSocket 和 Redis 都不是持久化状态来源。写 MySQL 后，客户端重连或重新打开文档时可以通过历史 update 恢复完整状态。

### Q24：Redis Pub/Sub 丢消息怎么办？

参考回答：

在这个设计里，Redis Pub/Sub 只是实时通知通道，不是最终状态来源。真正的协同 update 会先写入 MySQL。即使某个客户端因为网络或 Redis 问题没收到实时广播，断线重连或重新打开文档时，服务端会从 MySQL 加载完整 update 序列，通过 `sync:init` 恢复状态。

所以系统把可靠性放在 MySQL 持久化上，把低延迟分发放在 Redis Pub/Sub 上。

追问参考回答：

#### Q24-1：如果需要更强消息可靠性怎么做？

参考回答：可以使用 Redis Stream、Kafka 或消息队列，给消息加 offset 和消费确认。但协同编辑最终仍要以数据库或可靠日志作为状态来源。

#### Q24-2：Redis Stream 是否更合适？

参考回答：如果需求是可回放、可确认消费和处理离线消费者，Redis Stream 更合适。如果只是在线用户低延迟广播，Pub/Sub 更简单。

### Q25：如何避免实例消费自己发布的 Redis 消息？

参考回答：

每个 RedisBus 初始化时会生成一个 source id。发布消息时把 source、docId 和 body 包进 envelope。订阅端收到消息后，如果 envelope 的 source 和本实例 source 相同，就直接忽略。这样可以避免本实例先本地广播一次，又从 Redis 收到自己发布的消息后重复广播。

追问参考回答：

#### Q25-1：source id 重启后会变吗？

参考回答：会变。source id 只是运行期实例标识，用来避免本实例消费自己发布的消息，不需要跨重启保持稳定。

#### Q25-2：重复消息客户端能否容忍？

参考回答：Yjs 对相同 update 通常具备一定幂等性，但系统设计上仍应尽量避免重复广播。source id 去重就是服务端层面的优化。

## 7. 前端全栈类

### Q26：前端编辑器是怎么和后端协同的？

参考回答：

前端使用 Tiptap 作为富文本编辑器，使用 Yjs Collaboration 扩展作为协同状态层。组件创建 Y.Doc 后，将它传给 Tiptap 的 Collaboration 扩展。WebSocket 收到服务端 update 时，前端调用 `Y.applyUpdate(ydoc, update, "remote")` 应用远端变化。本地编辑产生 update 时，如果 origin 不是 remote，就通过 WebSocket 发给服务端。

这样编辑器 UI、协同算法和网络同步是分层的：Tiptap 负责编辑体验，Yjs 负责冲突合并，WebSocket 负责传输。

追问参考回答：

#### Q26-1：Tiptap 和 Yjs 分别解决什么问题？

参考回答：Tiptap 负责富文本编辑器 UI 和编辑命令，Yjs 负责多人协同状态和冲突合并。Tiptap 通过 Collaboration 扩展使用 Y.Doc。

#### Q26-2：origin 为什么要标记为 remote？

参考回答：用于区分远端同步产生的 update 和本地用户编辑产生的 update。远端 update 应用到本地后不能再次发送，否则会造成回声广播。

### Q27：导入导出为什么放在前端边界？

参考回答：

这个项目的后端保持格式无关，只持久化 Yjs 协同状态。Markdown、HTML、TXT 导入导出以及 PDF 打印都属于前端展示和格式转换问题。放在前端可以避免 Java 和 Go 后端各自实现一套格式转换逻辑，也能保持后端契约稳定。

导入时前端先预览和清洗，用户确认后再创建新文档并把内容进入编辑器初始化路径。导出时从当前编辑器 HTML 转成目标格式。

追问参考回答：

#### Q27-1：PDF 导出为什么是浏览器 print flow？

参考回答：PDF 导出和页面样式、字体、浏览器渲染强相关，前端用 print flow 可以复用当前 HTML 和样式模板，后端保持格式无关，也避免 Java 和 Go 重复实现 PDF 生成。

#### Q27-2：HTML 导入如何防 XSS？

参考回答：导入 HTML 时要做清洗和预览，只允许安全标签和属性，去掉 script、事件属性和危险 URL。用户确认后再进入编辑器内容，而不是直接信任原始 HTML。

### Q28：前端如何做环境切换？

参考回答：

前端通过配置区分 API_BASE 和 WS_BASE，环境切换由 `.env.local` 或 Vite mode 控制。这样前端源码不写死 Java 或 Go 后端地址，运行时可以选择连接 Java 后端或 Go 后端。

追问参考回答：

#### Q28-1：为什么不在代码里 if Java else Go？

参考回答：那会把后端实现差异泄漏到前端业务逻辑中。正确做法是让 Java 和 Go 都遵循同一契约，前端只通过环境变量切换 base URL。

#### Q28-2：本地和 Docker 端口不同怎么处理？

参考回答：通过 `.env.local` 或 Vite mode 配置 API 和 WebSocket 地址。源码不写死端口，本地和 Docker 只改环境配置。

## 8. 测试与工程能力类

### Q29：项目里有哪些测试？

参考回答：

Java 后端有认证、JWT、角色权限、WebSocket handler、指标等测试；前端有 API、配置、文档格式转换、导出样式、模板和协同编辑相关测试。Go 后端也有认证、配置、角色、WebSocket、指标和 JSON 测试。

测试重点不是追求数量，而是覆盖容易出问题的契约边界，比如权限判断、错误格式、WebSocket 消息、重连策略、导入导出格式转换。

追问参考回答：

#### Q29-1：WebSocket 怎么测？

参考回答：可以用 handler 单元测试模拟 session 和消息，验证连接鉴权、非法消息、viewer 发送 update 被拒绝、合法 update 会持久化和广播。端到端测试可以启动服务后用 WebSocket 客户端连接验证。

#### Q29-2：前后端契约怎么测？

参考回答：可以根据 OpenAPI 和 WebSocket 文档写契约测试，校验字段、状态码、错误格式和消息类型。Java 和 Go 后端都跑同一组契约用例。

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

追问参考回答：

#### Q30-1：构建和测试分别覆盖什么？

参考回答：测试验证逻辑正确性，比如权限、JWT、格式转换和 WebSocket 行为；构建验证代码能被完整编译打包，前端还会检查 TypeScript 和生产构建链路。

#### Q30-2：Docker Compose 里有哪些服务？

参考回答：主要包括 Web、Java 后端、MySQL、Redis，也可以通过 profile 启动 Go 后端和对应 Web 配置，用来验证同一前端切换不同后端。

### Q31：Prometheus 指标有什么用？

参考回答：

指标用于观察系统运行状态。项目中暴露了 HTTP 请求数、WebSocket 总连接数、活跃连接数和 WebSocket 消息数。这样可以在压测或线上运行时观察请求量、连接量和消息量，帮助定位流量波动、连接泄漏或消息异常。

追问参考回答：

#### Q31-1：还可以补充哪些指标？

参考回答：可以补充 WebSocket 错误数、update 写入耗时、Redis 发布失败数、Redis 广播耗时、单文档连接数、单文档 update 数、版本恢复耗时和鉴权失败次数。

#### Q31-2：如何定位 WebSocket 消息堆积？

参考回答：先看活跃连接数、消息输入输出速率、update 写入耗时和 Redis 发布耗时；再按 docId 找热点文档，检查是否数据库写入慢、广播慢或客户端消费慢。

## 9. 系统设计提升类

### Q32：如果 update 数量越来越多，打开文档变慢怎么办？

参考回答：

可以引入快照压缩机制。当前设计是加载并重放所有 Yjs updates，简单可靠，但长期运行后 update 数量会变大。优化方案是定期把一批 updates 合并成一个 compacted update 或 snapshot，保存为新的基线状态，再清理旧增量。客户端打开文档时先加载快照，再加载快照后的增量。

实现时要注意和在线编辑并发的关系，可以在事务中锁定文档，记录压缩边界 seq，确保压缩期间新增 update 不丢失。

追问参考回答：

#### Q32-1：压缩任务怎么触发？

参考回答：可以按 update 数量、累计大小、文档打开耗时或定时任务触发。例如同一文档 update 超过一定数量后异步执行压缩。

#### Q32-2：压缩失败如何回滚？

参考回答：压缩必须在事务中记录边界和写入快照。失败时回滚，不删除旧 update。只有快照写入成功并校验通过后，才清理边界之前的旧数据。

### Q33：如果并发用户很多，系统瓶颈在哪里？

参考回答：

主要瓶颈可能有四类。第一是 WebSocket 连接数，单实例内存和线程模型会受压力。第二是同一文档的 update 写入，因为当前同文档写入通过行锁串行化。第三是打开大文档时加载历史 updates 的耗时。第四是 Redis Pub/Sub 广播量。

优化方向包括 WebSocket 水平扩容、引入 update 快照压缩、热点文档分片或队列化写入、限制单次 update 大小、增加指标监控和压测。

追问参考回答：

#### Q33-1：同一篇热点文档如何优化？

参考回答：可以做 update 批量写入、快照压缩、限制编辑频率、热点文档单独分片或队列化处理，并增加单文档连接数和写入耗时指标。

#### Q33-2：多实例下 session 怎么管理？

参考回答：每个实例管理自己的本地 WebSocket session，跨实例通过 Redis 广播。需要全局在线统计时，可以把连接心跳写入 Redis，按实例和 sessionId 维护 TTL。

### Q34：为什么不直接把文档最终 HTML 存到数据库？

参考回答：

如果只存最终 HTML，后端需要处理多人同时编辑时的冲突合并，这会非常复杂。Yjs 的优势是用 CRDT 模型处理并发编辑冲突，服务端只需要保存和转发 update。最终 HTML 更适合导出或展示，不适合作为协同编辑的源状态。

不过可以额外维护一份只读预览 HTML，用于搜索或快速展示，但它不应该替代 Yjs 状态。

追问参考回答：

#### Q34-1：搜索文档内容怎么做？

参考回答：可以维护一份派生的纯文本索引。前端或后端在文档变更后生成 text snapshot，写入搜索字段或搜索引擎。它是派生数据，不替代 Yjs 状态来源。

#### Q34-2：后端是否需要理解富文本结构？

参考回答：当前后端不需要理解富文本结构，只保存 Yjs update。只有做内容搜索、敏感词检测、服务端导出等能力时，才需要引入派生解析。

### Q35：如何保证 Java 和 Go 后端行为一致？

参考回答：

核心是共享契约优先。REST 接口字段、错误格式、WebSocket 消息类型、SQL schema 和角色语义都放在 `packages/shared-contract` 和 `docs` 中。新增或修改能力时，先更新契约，再分别检查 Java 和 Go 实现，最后运行对应测试。

如果发现两端行为不一致，应该以共享契约为准，而不是让前端适配某一端特例。

追问参考回答：

#### Q35-1：契约变更流程是什么？

参考回答：先改共享契约，再改 Java 和 Go 实现，然后改前端调用，最后跑相关测试。如果是破坏性变更，需要考虑兼容旧客户端或版本化接口。

#### Q35-2：如何避免文档落后于实现？

参考回答：把契约文档作为开发入口，代码评审时检查 API、WebSocket、schema 变更是否同步更新文档，并通过契约测试减少人为遗漏。

## 10. 安全类

### Q36：这个项目有哪些安全设计？

参考回答：

主要包括：密码使用 BCrypt 哈希存储；REST 使用 Bearer JWT 鉴权；JWT 设置过期时间；WebSocket 通过子协议传 token，避免 token 出现在 URL；所有文档操作都做服务端权限校验；viewer 不能发送编辑 update；WebSocket update 有大小限制；错误响应使用统一结构，避免泄漏过多内部细节。

追问参考回答：

#### Q36-1：CORS 和 WebSocket Origin 怎么控制？

参考回答：通过 `ALLOWED_ORIGINS` 配置允许的前端来源。REST 请求做 CORS 控制，WebSocket 握手也应校验 Origin，防止非授权站点发起连接。

#### Q36-2：JWT secret 如何管理？

参考回答：不能写入源码或提交仓库，应该通过环境变量、密钥管理系统或部署平台 secret 注入。不同环境使用不同 secret，并定期轮换。

### Q37：如果 token 被盗怎么办？

参考回答：

短期内攻击者可能使用 token 访问接口，所以 token 必须设置合理 TTL，并且生产环境要使用 HTTPS，避免传输中泄漏。WebSocket 不把 token 放 URL，也是在减少日志和浏览器历史泄漏风险。

进一步可以做 refresh token、服务端 token denylist、用户主动退出失效、敏感操作二次校验等。

追问参考回答：

#### Q37-1：JWT 无状态和主动失效的矛盾怎么解决？

参考回答：JWT 天然无状态，主动失效困难。可以缩短 access token TTL，配合 refresh token；需要强制失效时维护 denylist 或 token version。

#### Q37-2：refresh token 怎么设计？

参考回答：access token 短期有效，refresh token 长期有效并安全存储。刷新时校验 refresh token、轮换新 token，并支持服务端撤销 refresh token。

## 11. 行为面试类

### Q38：这个项目中你遇到的最大技术难点是什么？

参考回答：

最大难点是实时协同编辑链路的边界划分。协同编辑涉及前端编辑器、Yjs 状态、WebSocket 传输、MySQL 持久化和 Redis 广播。如果边界不清晰，后端可能会承担不该承担的富文本合并逻辑，或者前端依赖某个后端实现细节。

最终设计是让 Yjs 负责冲突合并，前端负责编辑体验和格式转换，Java 后端负责鉴权、权限、update 持久化和广播，MySQL 作为状态来源，Redis 只做实时通知。这样每一层职责比较清楚。

追问参考回答：

#### Q38-1：你怎么验证这个方案是可行的？

参考回答：通过单元测试验证认证、权限和 WebSocket 行为，通过前端测试验证重连和格式转换，通过手动联调验证多用户编辑、只读用户限制、版本和评论通知。同时用共享契约检查 Java 和 Go 语义一致。

#### Q38-2：如果重新做一次会优化什么？

参考回答：我会更早引入 update 快照压缩和契约测试，并把 WebSocket 事件类型做得更体系化，比如增加文档恢复、权限变更和客户端 reload 通知。

### Q39：你怎么和前端或其他后端协作？

参考回答：

这个项目的协作核心是契约先行。因为同一个前端要适配 Java 和 Go 两套后端，所以接口字段、错误格式、WebSocket 消息和角色语义不能各写各的。开发时我会先看共享契约和文档，再改对应实现。如果改到 API、WebSocket 或 schema，也要同步更新契约文档，并检查另一套后端是否需要同步。

追问参考回答：

#### Q39-1：如果对契约设计有分歧怎么办？

参考回答：先回到业务语义讨论，而不是围绕某个端的实现便利争论。确认资源模型、权限边界、错误格式和兼容性后，再更新契约并同步两端实现。

#### Q39-2：怎么处理兼容旧客户端？

参考回答：非破坏性字段可以新增并让客户端忽略未知字段；破坏性变更要做版本化接口或保留旧字段一段时间。WebSocket 未知事件客户端也应该忽略。

### Q40：如果面试官让你现场改进这个项目，你会优先做什么？

参考回答：

我会优先做三件事。第一是 Yjs update 快照压缩，解决大文档打开慢和 update 表增长问题。第二是补充更完整的契约测试，确保 Java 和 Go 后端行为一致。第三是增强可观测性，例如增加 WebSocket 错误数、广播耗时、Redis 发布失败数、文档 update 写入耗时等指标。

这些改进都围绕系统长期运行的可靠性和可维护性，而不是单纯增加功能。

追问参考回答：

#### Q40-1：快照压缩怎么兼容历史版本？

参考回答：版本快照保存的是某个时间点的 update 序列或压缩后状态。压缩时不能破坏已保存版本，可以让版本引用独立快照数据，或者在清理旧 update 前确保版本状态已经独立保存。

#### Q40-2：哪个指标最能反映协同链路健康？

参考回答：最核心的是 update 写入耗时和 WebSocket 广播延迟。前者反映状态持久化是否堵塞，后者反映实时协作体验。再结合活跃连接数、错误数和 Redis 发布失败数综合判断。

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

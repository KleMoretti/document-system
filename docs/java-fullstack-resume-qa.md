# Java 全栈简历问答与面试官追问

本文基于本仓库的在线文档协同编辑系统生成，合并了简历项目问答和面试官视角深挖题，用于准备 Java 全栈项目面试。回答时建议坚持一个原则：先讲业务目标，再讲技术方案，最后讲权衡和结果。

## 1. 项目介绍类

### Q1：请介绍一下你简历上的在线文档协同编辑系统。

参考回答：

这是一个在线文档协同编辑系统，核心目标是让多个用户可以在浏览器里共同编辑同一篇富文本文档。前端使用 React、TypeScript、Tiptap 和 Yjs，Java 后端使用 Spring Boot 3、JdbcTemplate、MySQL、Redis 和 WebSocket。

系统支持用户注册登录、JWT 鉴权、文档创建和分享、owner/editor/viewer 权限控制、实时协同编辑、在线状态、版本保存和恢复、评论协作、软删除恢复、导入导出以及 Prometheus 指标。架构上前端只依赖明确的 REST 和 WebSocket 契约，当前问答以 Java 后端实现为准；MySQL 是最终状态来源，Redis 负责低延迟跨实例广播。

追问参考回答：

#### Q1-1：你主要负责哪一块？

参考回答：我主要负责 Java 后端核心链路，包括认证授权、文档 REST API、权限控制、WebSocket 协同同步、Yjs update 持久化、Redis 跨实例广播，以及相关测试和契约对齐。

#### Q1-2：为什么要维护 REST 和 WebSocket 契约？

参考回答：契约能让前端和 Java 后端边界稳定。REST 字段、错误格式、WebSocket 消息、角色语义和 SQL schema 都有明确说明后，前端不需要依赖某个控制器或 handler 的临时实现细节；Java 后端变更行为时，也能同步检查文档、前端类型和测试是否一致。

#### Q1-3：项目里最难的点是什么？

参考回答：最难的是协同编辑链路的边界划分。Yjs 负责冲突合并，前端负责编辑体验，后端负责鉴权、持久化和广播，MySQL 做状态来源，Redis 只做实时分发。边界清楚后，系统才容易维护和扩展。

### Q2：这个项目最能体现你 Java 能力的地方是什么？

参考回答：

我认为主要体现在三块。第一是 Spring Boot REST API 的完整业务建模，包括认证、文档、权限、版本和评论。第二是协同编辑 update 的持久化设计，服务端用按文档批量写入、事务和 `document_sequences` 保证同一文档下 seq 连续递增，避免并发编辑导致序号冲突。第三是 WebSocket 实时同步通道，连接时做 JWT 和文档权限校验，初始化时加载历史 Yjs update，编辑时先持久化再广播。

这些能力不是单纯 CRUD，而是涉及权限、安全、并发、一致性、实时通信和可观测性。

追问参考回答：

#### Q2-1：为什么不用 JPA？

参考回答：这个项目的数据访问以明确 SQL、事务、行锁和契约一致性为主，JdbcTemplate 更直观，能直接控制 `SELECT ... FOR UPDATE`、join 查询和批量读取。JPA 更适合领域对象关系比较稳定的场景，但这里很多逻辑和协同 update 序列有关，直接 SQL 可控性更强。

#### Q2-2：`@Transactional` 用在了哪里？

参考回答：主要用在创建文档、追加 Yjs update、保存快照、恢复版本等需要多条 SQL 保持一致的操作中。例如批量追加 update 时要锁定文档、读取并推进 `document_sequences.next_seq`、插入一批 update、按窗口更新文档更新时间，这些步骤必须在同一个事务里完成。

#### Q2-3：行锁具体锁的是什么？

参考回答：追加 update 时会先锁 `documents` 表中当前 `docId` 对应的文档行，再锁 `document_sequences` 中对应文档的序号行。文档行保证文档存在并给快照、版本恢复等操作建立一致边界；序号行负责分配本批次连续 seq。不同文档对应不同记录，通常不会互相阻塞。

### Q3：如果让你用一句话写到简历里，你会怎么写？

参考回答：

可以写成：

```text
基于 Java 21 + Spring Boot 3、React + Tiptap + Yjs、MySQL、Redis 和 WebSocket 实现在线文档协同编辑系统，支持 JWT 鉴权、RBAC 权限、实时同步、版本管理、评论协作和 Prometheus 指标。
```

如果需要突出后端：

```text
负责 Java 后端核心能力开发，设计文档权限模型和 Yjs 增量更新持久化方案，通过 MySQL 事务、`document_sequences` 连续序号和批量写入保证并发写入顺序，并使用 Redis Pub/Sub 支持 WebSocket 多实例广播。
```

追问参考回答：

#### Q3-1：这些技术分别解决什么问题？

参考回答：React 和 Tiptap 解决前端富文本编辑体验，Yjs 解决多人编辑冲突合并，Spring Boot 提供后端 API 和 WebSocket 能力，MySQL 保存最终状态，Redis 做跨实例实时广播，JWT 和 RBAC 解决认证授权，Docker Compose 解决本地环境编排。

#### Q3-2：哪个模块是你最熟悉的？

参考回答：我最熟悉 Java 后端的协同同步链路，包含 WebSocket 连接鉴权、Yjs update 持久化、权限校验、本地广播和 Redis Pub/Sub 跨实例广播。

## 2. 架构设计类

### Q4：系统整体架构是什么样的？

参考回答：

前端是 React + TypeScript + Tiptap + Yjs，负责编辑器交互、协同状态应用、导入导出和 API 调用。Java 后端对外提供 REST API 和 WebSocket 协同通道，负责认证、授权、文档元数据、Yjs update 持久化、评论和版本管理。MySQL 是最终状态来源，保存用户、文档、权限、更新序列、版本和评论。Redis 用于 WebSocket 消息跨实例广播。

核心数据流是：用户登录拿到 JWT，打开文档时先通过 REST 获取文档元数据，再建立 WebSocket 连接。后端校验 token 和权限，加载历史 Yjs updates 发给前端。用户编辑时，前端发送 Yjs update，后端写入 MySQL，再广播给其他在线客户端。

追问参考回答：

#### Q4-1：MySQL 和 Redis 分别承担什么角色？

参考回答：MySQL 是最终状态来源，保存用户、文档、权限、更新序列、版本和评论。Redis 只是实时广播通道，用来把一个实例收到的 WebSocket 消息转发到其他实例。

#### Q4-2：前端为什么不直接访问 Redis？

参考回答：Redis 属于后端基础设施，不能暴露给浏览器。前端直接访问 Redis 会带来鉴权、数据隔离和安全问题，也会破坏后端对业务权限的统一控制。

#### Q4-3：WebSocket 和 REST 的边界怎么划分？

参考回答：REST 负责资源型业务操作，比如登录、文档列表、分享、版本和评论；WebSocket 负责实时增量消息，比如协同 update、在线状态和评论变更通知。

### Q5：为什么前端不能依赖 Java 私有实现细节？

参考回答：

因为前端和后端的稳定边界应该是接口契约，而不是 Java 代码里的某个临时字段、错误文案或内部对象结构。如果前端依赖实现细节，后端重构 Controller、record 字段或 WebSocket handler 时就容易破坏页面行为。因此前端只依赖 REST、WebSocket 消息格式和 SQL 语义说明中明确承诺的行为。

这样做的好处是接口变更必须同步体现在契约和文档上，Java 后端、前端类型和测试可以围绕同一套行为检查，联调成本更低。

追问参考回答：

#### Q5-1：如果前端类型和 Java 返回字段不一致怎么办？

参考回答：先确认 `openapi.yml`、`websocket.md`、`docs/api-contract.md` 和当前 Java `Models.java` / handler 行为，判断是文档落后、前端类型落后，还是 Java 实现偏离。修正时要让契约、Java 返回和前端类型重新一致，不能只在 UI 里写临时兼容分支。

#### Q5-2：契约文档如何维护？

参考回答：新增或修改 REST、WebSocket、角色语义、错误格式、SQL schema 时，先更新 `packages/shared-contract` 和 `docs`，再同步检查 Java 实现和前端调用，最后跑对应测试。

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

后续 REST 请求通过 `Authorization: Bearer <token>` 传递令牌，后端解析并校验签名和过期时间，再拿到 userId 执行业务权限判断。

追问参考回答：

#### Q7-1：BCrypt 为什么适合存密码？

参考回答：BCrypt 自带盐并且计算成本可调，比普通哈希更能抵抗彩虹表和暴力破解。即使数据库泄漏，攻击者也很难快速还原明文密码。

#### Q7-2：JWT 泄漏怎么办？

参考回答：首先要用 HTTPS、避免令牌进入 URL、设置较短 TTL。进一步可以引入刷新令牌、服务端拒绝名单、主动退出失效和敏感操作二次校验。

#### Q7-3：令牌过期怎么处理？

参考回答：当前后端校验 `exp`，过期后返回未授权错误，前端应引导重新登录。生产系统一般会增加刷新令牌，在访问令牌过期时尝试刷新。

### Q8：WebSocket 如何做鉴权？

参考回答：

WebSocket 连接建立时，前端通过子协议传 JWT，例如 `new WebSocket(url, ["bearer", token])`。服务端从 `Sec-WebSocket-Protocol` 里解析令牌，校验 JWT 后拿到 userId，再根据 docId 查询用户在该文档中的角色。

如果令牌无效，会返回 `UNAUTHORIZED` 并关闭连接；如果用户没有文档权限，会返回 `FORBIDDEN` 并关闭连接。为了兼容旧客户端，服务端也保留了查询参数令牌的解析方式，但新客户端不应该把令牌放在 URL 中。

追问参考回答：

#### Q8-1：为什么令牌不推荐放 URL？

参考回答：URL 可能出现在浏览器历史、代理日志、访问日志和 Referer 中，泄漏风险更高。WebSocket 用子协议传令牌能减少这些暴露面。

#### Q8-2：WebSocket 握手后还能不能像 REST 一样每次请求带请求头？

参考回答：浏览器 WebSocket API 在连接建立后不能像 REST 那样给每条消息加 HTTP 请求头，所以通常在握手阶段完成认证，然后把 userId 和 role 存在 session 上，后续消息再做业务权限校验。

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

Java 后端接收 `sync:update` 后先进入 `UpdateBatcher`，同一文档默认达到 32 条或等待 25 ms 后批量落库。落库时使用事务，先锁定对应文档，再确保 `document_sequences` 中存在该文档的序号记录；首次写入时，序号初始值取 `document_updates` 最大 seq 和 `document_snapshots` 最大 last_seq 的较大值再加 1。随后锁定 `document_sequences` 行，读取 `next_seq` 作为本批次起始序号，批量插入 update，并把 `next_seq` 推进本批次大小。表上还有 `(document_id, seq)` 唯一约束，作为数据库层的兜底保护。

所以并发编辑时，同一个文档内 update 会被分配唯一、递增且连续的 seq，多个 update 还可以共享一次事务。

追问参考回答：

#### Q13-1：`FOR UPDATE` 不加事务会怎样？

参考回答：行锁依赖事务边界。如果不在事务中，锁可能很快释放，无法覆盖后续读取 `next_seq`、插入 update、推进 `next_seq` 的步骤，就不能保证并发安全。

#### Q13-2：不同文档之间会不会互相阻塞？

参考回答：正常不会。行锁锁的是指定 docId 的文档行，不同文档对应不同记录，可以并发写入。

#### Q13-3：唯一约束报错后如何处理？

参考回答：唯一约束是兜底保护。当前主路径通过 `document_sequences` 分配序号，正常不应冲突；生产里如果仍遇到唯一约束异常，可以捕获后重试整个批量追加流程，重新获取锁和序号。

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

这个项目的 Java 后端保持格式无关，只持久化 Yjs 协同状态。Markdown、HTML、TXT 导入导出以及 PDF 打印都属于前端展示和格式转换问题。放在前端可以让后端专注认证、权限、协同状态持久化和广播，也能保持 REST/WebSocket 契约稳定。

导入时前端先预览和清洗，用户确认后再创建新文档并把内容进入编辑器初始化路径。导出时从当前编辑器 HTML 转成目标格式。

追问参考回答：

#### Q27-1：PDF 导出为什么是浏览器打印流程？

参考回答：PDF 导出和页面样式、字体、浏览器渲染强相关，前端用打印流程可以复用当前 HTML 和样式模板。后端生成 PDF 需要引入 HTML 渲染引擎或无头浏览器，会把格式转换复杂度带入 Java 服务，而这不是当前后端的核心职责。

#### Q27-2：HTML 导入如何防 XSS？

参考回答：导入 HTML 时要做清洗和预览，只允许安全标签和属性，去掉 script、事件属性和危险 URL。用户确认后再进入编辑器内容，而不是直接信任原始 HTML。

### Q28：前端如何做环境切换？

参考回答：

前端通过配置区分 API_BASE 和 WS_BASE，环境切换由 `.env.local` 或 Vite mode 控制。这样前端源码不写死本地、Docker 或线上 Java 后端地址，部署时只需要调整运行环境配置。

追问参考回答：

#### Q28-1：为什么不在业务代码里写死环境判断？

参考回答：环境差异属于部署配置，不属于业务逻辑。如果在组件里写死端口、域名或环境分支，后续本地、Docker、测试环境和生产环境都会难以维护。正确做法是前端只从配置读取 API 和 WebSocket 地址。

#### Q28-2：本地和 Docker 端口不同怎么处理？

参考回答：通过 `.env.local` 或 Vite mode 配置 API 和 WebSocket 地址。源码不写死端口，本地和 Docker 只改环境配置。

## 8. 测试与工程能力类

### Q29：项目里有哪些测试？

参考回答：

Java 后端有认证、JWT、角色权限、WebSocket handler、健康检查、指标等测试；前端有 API、配置、文档格式转换、导出样式、模板和协同编辑相关测试。

测试重点不是追求数量，而是覆盖容易出问题的契约边界，比如权限判断、错误格式、WebSocket 消息、重连策略、导入导出格式转换。

追问参考回答：

#### Q29-1：WebSocket 怎么测？

参考回答：可以用 handler 单元测试模拟 session 和消息，验证连接鉴权、非法消息、viewer 发送 update 被拒绝、合法 update 会持久化和广播。端到端测试可以启动服务后用 WebSocket 客户端连接验证。

#### Q29-2：前后端契约怎么测？

参考回答：可以根据 OpenAPI 和 WebSocket 文档写契约测试，校验 Java 返回的字段、状态码、错误格式和消息类型，再用前端类型和 API 封装做同向校验，避免文档、后端和前端类型分叉。

### Q30：如何验证这个项目？

参考回答：

常用命令包括：

```powershell
npm run test:web
npm run build:web
npm run test:java
```

本地运行时可以用 Docker Compose 启动 Web、Java API、MySQL 和 Redis，也可以分别启动前端和 Java 后端。Java 后端暴露 `/healthz`、`/readyz` 和 `/metrics`，其中 `/readyz` 会检查 MySQL，`/metrics` 返回 Prometheus 文本格式。

追问参考回答：

#### Q30-1：构建和测试分别覆盖什么？

参考回答：测试验证逻辑正确性，比如权限、JWT、格式转换和 WebSocket 行为；构建验证代码能被完整编译打包，前端还会检查 TypeScript 和生产构建链路。

#### Q30-2：Docker Compose 里有哪些服务？

参考回答：主要包括 Web、Java 后端、MySQL、Redis。Java 后端需要通过环境变量配置 MySQL、Redis、`JWT_SECRET`、`ALLOWED_ORIGINS` 以及 WebSocket 高并发相关参数。

### Q31：Prometheus 指标有什么用？

参考回答：

指标用于观察系统运行状态。当前 Java 后端暴露 HTTP 请求数、WebSocket 总连接数、活跃连接数、消息类型计数、错误码计数、消息字节数、慢客户端数、出站队列最大深度、广播耗时、批量持久化耗时和批量大小。这样可以在压测或线上运行时判断瓶颈来自请求量、连接泄漏、数据库写入、广播扇出还是慢客户端。

追问参考回答：

#### Q31-1：还可以补充哪些指标？

参考回答：当前已经有 WebSocket 错误数、update 持久化耗时、广播耗时、批量大小和慢客户端指标。后续可以补充 Redis 发布失败计数、按文档聚合但控制基数的热点文档指标、`sync:init` payload 大小、快照命中率、版本恢复耗时和鉴权失败次数。

#### Q31-2：如何定位 WebSocket 消息堆积？

参考回答：先看活跃连接数、`documentation_collab_ws_send_queue_depth_max`、`documentation_collab_ws_slow_clients_total`、广播耗时和 update 持久化耗时；如果队列深度接近 `WS_SEND_QUEUE_SIZE` 或出现 `SLOW_CLIENT`，说明至少有客户端消费速度跟不上广播。如果持久化耗时升高，则重点看 `UpdateBatcher`、数据库连接池和热点文档写入。

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

主要瓶颈可能有四类。第一是 WebSocket 连接数和出站队列，慢客户端会拖累单文档广播扇出。第二是同一文档的 update 写入，当前已做按文档批量写入，但同文档序号分配和事务提交仍需要串行化。第三是打开大文档时加载历史 updates 的耗时。第四是 Redis Pub/Sub 广播量。

优化方向包括 WebSocket 水平扩容、调大或调小出站队列和批量窗口、引入或加强 update 快照压缩、热点文档分片或专用协同服务、限制单次 update 大小、增加指标监控和压测。

追问参考回答：

#### Q33-1：同一篇热点文档如何优化？

参考回答：当前已经做了前端 update 合并、后端按文档批量写入和慢客户端断开。继续优化可以从快照压缩、限制编辑频率、热点文档单独分片、专用协同服务、单文档连接数指标和写入耗时指标入手。

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

### Q35：如何保证前端和 Java 后端行为一致？

参考回答：

核心是契约优先。REST 接口字段、错误格式、WebSocket 消息类型、SQL schema 和角色语义都放在 `packages/shared-contract` 和 `docs` 中。新增或修改能力时，先更新契约，再检查 Java 实现、前端类型和调用代码，最后运行对应测试。

如果发现文档、Java 代码和前端类型不一致，应该先确认真实业务语义，再把三者改回同一个行为，不能让前端靠临时特例掩盖后端语义变化。

追问参考回答：

#### Q35-1：契约变更流程是什么？

参考回答：先改契约和文档，再改 Java 实现，然后改前端调用和类型，最后跑相关测试。如果是破坏性变更，需要考虑兼容旧客户端或版本化接口。

#### Q35-2：如何避免文档落后于实现？

参考回答：把契约文档作为开发入口，代码评审时检查 API、WebSocket、schema 变更是否同步更新文档，并通过契约测试减少人为遗漏。

## 10. 安全类

### Q36：这个项目有哪些安全设计？

参考回答：

主要包括：密码使用 BCrypt 哈希存储；REST 使用 Bearer JWT 鉴权；JWT 设置过期时间；WebSocket 通过子协议传令牌，避免令牌出现在 URL；所有文档操作都做服务端权限校验；viewer 不能发送编辑 update；WebSocket update 有大小限制；错误响应使用统一结构，避免泄漏过多内部细节。

追问参考回答：

#### Q36-1：CORS 和 WebSocket Origin 怎么控制？

参考回答：通过 `ALLOWED_ORIGINS` 配置允许的前端来源。REST 请求做 CORS 控制，WebSocket 握手也应校验 Origin，防止非授权站点发起连接。

#### Q36-2：JWT secret 如何管理？

参考回答：不能写入源码或提交仓库，应该通过环境变量、密钥管理系统或部署平台 secret 注入。不同环境使用不同 secret，并定期轮换。

### Q37：如果令牌被盗怎么办？

参考回答：

短期内攻击者可能使用令牌访问接口，所以令牌必须设置合理 TTL，并且生产环境要使用 HTTPS，避免传输中泄漏。WebSocket 不把令牌放 URL，也是在减少日志和浏览器历史泄漏风险。

进一步可以做刷新令牌、服务端令牌拒绝名单、用户主动退出失效、敏感操作二次校验等。

追问参考回答：

#### Q37-1：JWT 无状态和主动失效的矛盾怎么解决？

参考回答：JWT 天然无状态，主动失效困难。可以缩短访问令牌 TTL，配合刷新令牌；需要强制失效时维护拒绝名单或令牌版本。

#### Q37-2：刷新令牌怎么设计？

参考回答：访问令牌短期有效，刷新令牌长期有效并安全存储。刷新时校验刷新令牌、轮换新令牌，并支持服务端撤销刷新令牌。

## 11. 行为面试类

### Q38：这个项目中你遇到的最大技术难点是什么？

参考回答：

最大难点是实时协同编辑链路的边界划分。协同编辑涉及前端编辑器、Yjs 状态、WebSocket 传输、MySQL 持久化和 Redis 广播。如果边界不清晰，后端可能会承担不该承担的富文本合并逻辑，或者前端依赖某个后端实现细节。

最终设计是让 Yjs 负责冲突合并，前端负责编辑体验和格式转换，Java 后端负责鉴权、权限、update 持久化和广播，MySQL 作为状态来源，Redis 只做实时通知。这样每一层职责比较清楚。

追问参考回答：

#### Q38-1：你怎么验证这个方案是可行的？

参考回答：通过单元测试验证认证、权限和 WebSocket 行为，通过前端测试验证重连和格式转换，通过手动联调验证多用户编辑、只读用户限制、版本和评论通知。同时用契约文档检查 Java 返回、WebSocket 消息和前端类型是否一致。

#### Q38-2：如果重新做一次会优化什么？

参考回答：我会更早引入 update 快照压缩和契约测试，并把 WebSocket 事件类型做得更体系化，比如增加文档恢复、权限变更和客户端 reload 通知。

### Q39：你怎么和前端或其他后端协作？

参考回答：

这个项目的协作核心是契约先行。接口字段、错误格式、WebSocket 消息和角色语义不能在前端和 Java 后端各写各的。开发时我会先看契约和文档，再改 Java 实现和前端调用。如果改到 API、WebSocket 或 schema，也要同步更新契约文档和测试。

追问参考回答：

#### Q39-1：如果对契约设计有分歧怎么办？

参考回答：先回到业务语义讨论，而不是围绕某个端的实现便利争论。确认资源模型、权限边界、错误格式和兼容性后，再更新契约，并同步 Java 实现和前端调用。

#### Q39-2：怎么处理兼容旧客户端？

参考回答：非破坏性字段可以新增并让客户端忽略未知字段；破坏性变更要做版本化接口或保留旧字段一段时间。WebSocket 未知事件客户端也应该忽略。

### Q40：如果面试官让你现场改进这个项目，你会优先做什么？

参考回答：

我会优先做三件事。第一是把快照压缩从客户端触发进一步演进为后端任务或更明确的压缩策略，解决大文档打开慢和 update 表增长问题。第二是补充更完整的契约测试，确保 Java 返回、WebSocket 消息和前端类型一致。第三是增强可观测性，例如补充 Redis 发布失败、`sync:init` payload 大小、快照命中率和版本恢复耗时等指标。

这些改进都围绕系统长期运行的可靠性和可维护性，而不是单纯增加功能。

追问参考回答：

#### Q40-1：快照压缩怎么兼容历史版本？

参考回答：版本快照保存的是某个时间点的 update 序列或压缩后状态。压缩时不能破坏已保存版本，可以让版本引用独立快照数据，或者在清理旧 update 前确保版本状态已经独立保存。

#### Q40-2：哪个指标最能反映协同链路健康？

参考回答：最核心的是 update 写入耗时和 WebSocket 广播延迟。前者反映状态持久化是否堵塞，后者反映实时协作体验。再结合活跃连接数、错误数和 Redis 发布失败数综合判断。

## 12. 简历答辩模板

### 30 秒版本

```text
我做的是一个在线文档协同编辑系统，前端用 React、Tiptap 和 Yjs，后端用 Java 21、Spring Boot、MySQL、Redis 和 WebSocket。系统支持登录鉴权、文档权限、实时协同、在线状态、版本、评论和导入导出。我主要关注 Java 后端的认证授权、Yjs update 持久化、WebSocket 同步、批量落库、慢客户端隔离和 Redis 跨实例广播。
```

### 2 分钟版本

```text
这个项目是在线文档协同编辑系统，业务上类似多人同时编辑同一份富文本文档。前端使用 React + TypeScript + Tiptap + Yjs，Java 后端使用 Spring Boot 3，数据层使用 MySQL，实时广播使用 WebSocket 和 Redis Pub/Sub。

用户登录后拿到 JWT，打开文档时后端会校验 token 和用户在文档中的角色。服务端从 MySQL 加载该文档快照和历史 Yjs updates，通过 sync:init 初始化客户端。用户编辑时，前端在 35 ms 窗口内合并 Yjs update，通过 WebSocket 发给后端。后端判断 owner/editor 权限，按文档进入批量写入队列，使用事务和 `document_sequences` 按 seq 追加写入 document_updates，然后广播给本地 WebSocket session，并通过 Redis 发给其他实例。

项目还实现了 owner/editor/viewer 权限、文档分享、软删除恢复、版本快照、评论协作、导入导出和 Prometheus 指标。架构上前端只依赖明确的 REST 和 WebSocket 契约，Java 后端负责兑现这些契约并维护 MySQL 最终状态和 Redis 实时广播。
```

### 项目亮点版本

```text
这个项目的亮点主要有三个。第一是实时协同链路，使用 Yjs 处理并发编辑冲突，后端只负责 update 持久化和广播，降低了服务端复杂度。第二是高并发写入优化，同一文档的 update 通过前端合并、后端批量落库、`document_sequences` 连续序号和唯一约束保证顺序。第三是可扩展性，单实例内使用 WebSocket 出站队列隔离慢客户端，多实例通过 Redis Pub/Sub 转发，同时 MySQL 作为最终状态来源，客户端断线后可以通过 sync:init 恢复。
```

## 13. 面试官深挖题库

本部分保留面试官视角的深挖问题、评分标准和项目回答模板。

本文基于简历项目“在线文档协同编辑系统”整理，视角是 Java 全栈面试官会如何根据项目描述继续深挖，以及候选人应该如何回答。适用于 Java 全栈开发、Java 后端开发、协同办公/实时系统方向面试。

### 0. 面试官会先看什么

简历里最容易引发追问的关键词：

- React 19、TypeScript、Vite、Tiptap、Yjs
- Java 21、Spring Boot 3、WebSocket
- MySQL、Redis、JWT
- 在线文档协同编辑、实时协作、分享授权、评论回复、版本保存与恢复
- 前后端契约、OpenAPI、WebSocket 消息协议、MySQL Schema
- Java 后端认证、批量落库、WebSocket 出站队列和 Prometheus 指标

面试官通常不是只问“你用了什么技术”，而是会追问：

- 这个技术在项目里解决了什么问题。
- 你负责的边界在哪里。
- 关键链路怎么走。
- 并发、一致性、权限、安全、扩展性怎么保证。
- 如果用户量变大或出现异常，系统怎么演进。

回答原则：

- 先讲业务目标，再讲技术方案，最后讲权衡和改进。
- 不要只背技术名词，要把前端、Java 后端、MySQL、Redis、WebSocket 串成链路。
- 遇到不会的追问，可以承认当前项目边界，再给出合理演进方案。

### Q1：请你用 2 分钟介绍一下这个项目，你主要负责什么？

难度：基础

面试官考察点：

- 是否能清晰讲业务背景和系统边界。
- 是否真正参与核心模块，而不是只会念技术栈。
- 是否能把前端和 Java 后端串起来。

回答结构：

1. 项目是什么。
2. 核心功能有哪些。
3. 技术架构是什么。
4. 你负责哪些关键模块。
5. 项目亮点是什么。

参考回答：

这个项目是一个在线文档协同编辑系统，目标是让多个用户可以在浏览器里共同编辑同一篇富文本文档。功能上支持用户登录、文档创建、分享授权、owner/editor/viewer 权限控制、多人实时编辑、在线状态、评论回复、版本保存与恢复、回收站恢复，以及 Markdown/HTML/TXT 导入导出和 PDF 导出。

技术上，前端使用 React 19、TypeScript、Vite、Tiptap 和 Yjs。Tiptap 负责富文本编辑体验，Yjs 负责协同编辑状态合并，WebSocket 负责实时同步。Java 后端使用 Java 21 和 Spring Boot 3，提供 REST API、WebSocket 通道、JWT 鉴权、MySQL 持久化和 Redis Pub/Sub 多实例广播。

我主要负责前后端核心功能开发，包括前端协同编辑器接入、Java 后端认证授权、文档权限模型、Yjs update 持久化、WebSocket 初始化同步和广播，以及 OpenAPI、WebSocket 协议和 SQL Schema 的契约维护。项目亮点是前端只依赖明确契约，Java 后端负责稳定兑现这些契约；同时用 MySQL 作为最终状态来源，用 Redis 做低延迟跨实例广播。

高质量加分点：

- 明确说出“Yjs 负责协同冲突合并，服务端不解析富文本语义”。
- 明确说出“REST 管业务资源，WebSocket 管实时增量”。
- 明确说出“前端和 Java 后端靠契约对齐，避免实现细节泄漏到 UI”。

可能追问：

- 项目里最难的点是什么？
- 你具体写了哪些 Java 类或前端组件？
- 为什么只有 Java 后端也要强调契约？

追问参考回答：

- 项目里最难的点是什么？

  最难的是实时协同链路的边界划分和一致性保证。协同编辑涉及前端编辑器、Yjs 状态、WebSocket 传输、MySQL 持久化和 Redis 多实例广播，如果边界不清晰，后端可能会承担富文本冲突合并，复杂度会很高。我的处理方式是让 Yjs 负责冲突合并，Java 后端只负责鉴权、权限、update 持久化和广播，并用 MySQL 作为最终状态来源。

- 你具体写了哪些 Java 类或前端组件？

  前端主要是 `App.tsx`、`CollaborativeEditor.tsx`、`api.ts`、`documentFormats.ts` 和 `exportStyles.ts` 相关能力；Java 后端主要是 `AuthController`、`DocumentController`、`DocumentSocketHandler`、`AppRepository`、`JwtManager`、`Roles`、`RedisBus` 这些核心类。对应模块覆盖登录鉴权、文档 REST API、WebSocket 同步、Yjs update 落库、权限控制和跨实例广播。

- 为什么只有 Java 后端也要强调契约？

  即使只有一个后端，契约也能让前后端边界稳定，降低联调成本。REST 字段、WebSocket 消息、错误格式、权限语义和 SQL schema 都要有明确来源，前端不能适配 Java 某段临时代码的私有行为，否则后端重构或字段调整时就会出问题。

### Q2：为什么这个项目需要 Yjs？为什么不自己处理多人编辑冲突？

难度：中等

面试官考察点：

- 是否理解协同编辑的本质不是普通 CRUD。
- 是否知道 Yjs update 是增量协同状态，不是最终 HTML。
- 是否能解释技术选型的复杂度权衡。

回答结构：

1. 多人协同编辑的核心问题。
2. 自己实现冲突合并的复杂点。
3. Yjs 在项目中的职责。
4. 服务端为什么只存 update。

参考回答：

多人协同编辑的核心问题是并发修改冲突。比如两个人同时在同一段文字插入、删除或修改内容，如果后端只保存最终 HTML，就必须判断谁先谁后、如何合并光标位置、如何处理删除和插入交叉，这个复杂度很高。

Yjs 是 CRDT 协同编辑库，它能把用户本地编辑变成 update，并且支持不同客户端以不同顺序接收 update 后仍然收敛到一致状态。因此项目里让 Yjs 负责冲突合并，前端 Tiptap 只负责编辑器 UI，Java 后端不解析富文本语义，只做四件事：鉴权、权限判断、update 持久化、广播分发。

这种设计降低了服务端复杂度。服务端把 Yjs update 作为二进制增量保存到 MySQL，客户端打开文档时按顺序加载并 apply update，就可以重建文档状态。后续如果 update 太多，再通过 snapshot 压缩解决性能问题。

可能追问：

- CRDT 和 OT 有什么区别？
- Yjs update 为什么要按顺序保存？
- 如果一个 update 丢了会怎么样？

追问参考回答：

- CRDT 和 OT 有什么区别？

  OT 更依赖中心化的操作转换，服务端或协同层需要根据并发操作的上下文做 transform，典型场景是多人同时编辑文本时调整操作位置。CRDT 更强调数据结构本身可以在不同副本上独立更新，之后通过合并达到最终一致。Yjs 属于 CRDT 思路，适合前端本地先编辑、服务端转发和持久化 update 的模式。

- Yjs update 为什么要按顺序保存？

  Yjs 对 update 的应用顺序有一定容忍度，但服务端按 seq 保存有工程价值。第一，它能保证初始化时有稳定可重放的顺序；第二，它能作为 snapshot 的边界，比如 snapshotSeq 之前的 update 可以清理；第三，它能支撑版本保存和恢复，便于排查某篇文档的状态演进。

- 如果一个 update 丢了会怎么样？

  如果 update 已经持久化到 MySQL，但实时广播丢了，客户端重连后可以通过 `sync:init` 补齐，所以最终状态不会丢。如果 update 在写入 MySQL 前就丢了，那这次编辑不会进入服务端状态，生产环境可以通过客户端发送确认、失败重试、WebSocket 断线提示等机制增强可靠性。

Yjs 对不同客户端接收 update 的顺序有较强容忍，但服务端持久化仍然需要保证 update 不丢、可重放、可定位。按 seq 保存能让初始化、版本保存和快照压缩有明确边界，也方便排查问题。

### Q3：一次实时编辑从前端到 Java 后端再到其他用户，完整链路是什么？

难度：中等

面试官考察点：

- 是否能讲清端到端链路。
- 是否理解 Tiptap、Yjs、WebSocket、MySQL、Redis 的边界。
- 是否知道本地 update 和远端 update 如何避免循环。

参考回答：

用户在 Tiptap 编辑器中输入内容后，Tiptap 的 Yjs Collaboration 扩展会让本地 Y.Doc 产生一个 update。前端监听 `ydoc.on("update")`，如果这个 update 不是远端来源，并且 WebSocket 已连接、当前用户不是只读，就把二进制 update 编码成 Base64，发送 `sync:update` 消息给 Java 后端。

Java WebSocket handler 收到消息后，先从 session attributes 中拿到 docId、userId 和 role。服务端会判断 role 是否是 owner 或 editor，viewer 不能发送编辑 update。校验通过后，服务端解码 Base64，并检查 update 大小是否超过限制。然后通过仓储层把 update 写入 MySQL 的 `document_updates` 表，并为同一文档分配递增 seq。

写入成功后，服务端把同一个 update 广播给当前 Java 实例内连接到该文档的 WebSocket session，同时通过 Redis Pub/Sub 发布到 `doc:{docId}` 频道。其他 Java 实例收到 Redis 消息后，再广播给自己本地连接的用户。其他前端收到 `sync:update` 后调用 `Y.applyUpdate(ydoc, update, "remote")` 应用到本地文档。前端发送本地 update 时会检查 origin，避免把 remote update 再回发给服务端。

可能追问：

- 为什么要先写 MySQL 再广播？
- Redis 消息丢了怎么办？
- 前端如何判断只读？

追问参考回答：

- 为什么要先写 MySQL 再广播？

  因为 MySQL 是最终状态来源。如果先广播再写库，写库失败时其他客户端已经应用了一个服务端无法恢复的 update，状态会不一致。先写 MySQL 再广播，即使广播失败，客户端重新连接时仍能从 MySQL 加载到完整状态。

- Redis 消息丢了怎么办？

  Redis Pub/Sub 在这里不是可靠消息队列，而是实时通知通道。消息丢了会影响实时性，但不会影响最终状态，因为 update 已经写入 MySQL。客户端断线重连或重新打开文档时，Java 后端会从 MySQL 读取 snapshot 和 updates，通过 `sync:init` 补齐状态。

- 前端如何判断只读？

  前端根据当前文档的 `role` 和 `deletedAt` 派生 `canEdit`。只有文档未删除且角色是 owner 或 editor 时，编辑器才可编辑；viewer 或回收站文档会进入只读或不可编辑状态。但这只是体验控制，真正的权限校验仍在 Java 后端，WebSocket 收到 `sync:update` 时还会检查 session 中的 role。

高质量补充：

先持久化再广播，是为了让 MySQL 成为最终状态来源。即使广播失败或客户端断线，用户重新连接后也能通过 `sync:init` 从 MySQL 恢复状态。Redis 只负责实时性，不负责最终一致性。

### Q4：Java 后端如何保证同一文档的 update 序号不冲突？

难度：困难

面试官考察点：

- 是否理解数据库事务、行锁和连续序号分配。
- 是否知道协同编辑写路径中的并发风险。
- 是否能讲出批量写入、序号表和唯一约束兜底。

参考回答：

风险点在于多个用户可能同时编辑同一篇文档，多个 WebSocket 消息几乎同时到达后端。如果服务端只是每条消息都先查最大 seq 再加 1，两个事务可能读到相同最大值，导致插入相同 seq；即使加锁，热点文档也会因为频繁单条写入放大数据库压力。

当前实现分两层削峰。前端先在 35 ms 窗口内合并本地 Yjs update；Java 后端收到 `sync:update` 后进入 `UpdateBatcher`，同一文档默认达到 32 条或等待 25 ms 后批量落库。落库时开启事务，先锁定文档行，再确保 `document_sequences` 中有该文档的序号记录。首次写入时，序号初始值取 `document_updates` 最大 seq 和 `document_snapshots` 最大 last_seq 的较大值再加 1；之后锁定 `document_sequences` 行，用 `next_seq` 作为本批次起始序号，批量插入 update，并把 `next_seq` 推进本批次大小。

数据库层面，`document_updates` 上还有 `(document_id, seq)` 唯一约束，作为最后兜底。这样同一篇文档的 update 序号是唯一、递增且连续的；不同文档使用不同的文档行和序号行，通常不会互相阻塞。

可能追问：

- `FOR UPDATE` 不放在事务里会怎样？
- 为什么还要保留第一次初始化 `next_seq` 时对 snapshots 的 last_seq 判断？
- 热点文档并发很高时这个设计有什么瓶颈？

追问参考回答：

- `FOR UPDATE` 不放在事务里会怎样？

  `FOR UPDATE` 的锁生命周期依赖事务。如果没有事务，数据库执行完这条语句后锁很快释放，后续读取 `next_seq`、插入 update、推进 `next_seq` 就不再受锁保护，仍然可能出现并发事务分配相同 seq 的问题。所以追加 update 必须放在事务里，让锁覆盖“取序号 + 插入 update + 推进序号”的完整过程。

- 为什么还要保留第一次初始化 `next_seq` 时对 snapshots 的 last_seq 判断？

  因为引入快照后，`document_updates` 中一部分旧 update 会被删除，但它们已经被 snapshot 覆盖。如果新建 `document_sequences` 记录时只看 updates 表的最大 seq，可能会回退到小于等于 snapshotSeq 的序号。取 updates 最大 seq 和 snapshots 最大 last_seq 的较大值再加 1，可以保证迁移或首次写入时序号持续递增。

- 热点文档并发很高时这个设计有什么瓶颈？

  当前已经做了前端合并、服务端按文档批量写入和连续序号表，但同一文档的事务提交和 seq 分配仍需要串行化。热点文档编辑人数很多时，瓶颈会转向批量落库耗时、出站队列、单文档广播扇出和慢客户端。后续可以调优批量窗口、提高 snapshot 压缩收益、限制单文档连接数，或者把协同同步独立成专门服务。

这个设计牺牲了同一文档写入的完全并行度，换来序列一致性，并通过前端合并和后端批量写入降低事务次数。对于热点文档，仍需要结合 `documentation_collab_ws_persist_duration_ms_*`、`documentation_collab_ws_send_queue_depth_max` 和慢客户端指标判断瓶颈。

### Q5：版本保存和版本恢复是怎么实现的？为什么恢复后要让前端重新加载？

难度：中等偏难

面试官考察点：

- 是否理解版本不是普通文本快照。
- 是否知道 Yjs 状态重建方式。
- 是否能意识到在线客户端本地状态和服务端恢复状态的冲突。

参考回答：

这个项目的版本保存不是保存 HTML，而是保存服务端当前持久化的 Yjs 状态。创建版本时，Java 后端读取当前文档的 snapshot 和 snapshot 之后的增量 updates，把每段二进制 update 转成 Base64，再写入 `document_versions.state_data`。这样版本仍然保持 Yjs update 模型。

恢复版本时，后端会开启事务，锁定文档，删除当前 `document_updates` 和 `document_snapshots`，再把版本中的 update 序列按 seq 从 1 开始重新写回 `document_updates`。随后更新文档更新时间，并通过 WebSocket 广播 `document:restored`。

前端收到 `document:restored` 后需要重新加载，是因为活动客户端内存里还保留恢复前的 Y.Doc 状态。如果继续基于旧状态发送 update，可能把旧内容重新写回服务端，覆盖恢复结果。重新加载可以让客户端从服务端新的持久化状态重新初始化。

可能追问：

- 恢复版本期间有人正在编辑怎么办？
- 版本数据很大怎么优化？
- 为什么不直接存一份 HTML？

追问参考回答：

- 恢复版本期间有人正在编辑怎么办？

  当前实现会在恢复时用事务锁定文档，替换服务端持久化的 updates 和 snapshots，然后广播 `document:restored` 让客户端重新加载。这样能避免恢复后的客户端继续基于旧状态编辑。更强的方案是加入文档 revision 或恢复期间写锁，恢复过程中拒绝新的 `sync:update`，客户端收到恢复事件后销毁旧 Y.Doc 并重新同步。

- 版本数据很大怎么优化？

  可以把版本从“完整 update 序列”优化成“snapshot + 增量”的结构，或者只保存压缩后的 Yjs state update。再进一步可以做版本保留策略，比如只保留最近 N 个手动版本、自动版本按时间归档，历史大对象放到对象存储，MySQL 只保存元数据和索引。

- 为什么不直接存一份 HTML？

  HTML 是展示结果，不是协同编辑的源状态。直接存 HTML 会丢失 Yjs 的协同结构，恢复后很难和后续 Yjs update 继续衔接。保存 Yjs update 或 snapshot 可以保持协同模型一致，客户端恢复后仍然通过同一套 `sync:init` 逻辑重建状态。

高质量补充：

当前设计是项目级实现，恢复版本通过通知前端刷新来避免旧本地状态继续写入。更强的生产方案可以加入文档 revision、恢复期间短暂写锁、客户端收到恢复事件后主动销毁旧 Y.Doc 并重新同步。

### Q6：Redis Pub/Sub 在项目中解决什么问题？如果 Redis 丢消息怎么办？

难度：中等

面试官考察点：

- 是否理解单实例 WebSocket 和多实例 WebSocket 的区别。
- 是否知道 Redis Pub/Sub 不是可靠消息队列。
- 是否能说明 MySQL 和 Redis 的职责边界。

参考回答：

单实例情况下，Java 后端可以把消息广播给自己内存中的 WebSocket session。但如果部署多个实例，用户 A 连接实例 1，用户 B 连接实例 2，实例 1 只做本地广播就无法通知用户 B。

所以项目里引入 Redis Pub/Sub 做跨实例广播。Java 后端把文档相关消息发布到 `doc:{docId}` 频道，其他实例订阅 `doc:*`，收到消息后广播给自己的本地 WebSocket session。为了避免本实例重复消费自己发出的消息，每个 Java 实例都有一个 source id，收到 Redis envelope 时如果 source 是自己就忽略。

如果 Redis Pub/Sub 丢消息，实时通知可能丢失，但文档状态不会丢。因为编辑 update 是先写 MySQL，再广播。客户端断线重连或重新打开文档时，后端会从 MySQL 加载 snapshot 和 updates，通过 `sync:init` 恢复状态。因此 MySQL 是最终状态来源，Redis 只是低延迟通知通道。

可能追问：

- Redis Pub/Sub 和 Redis Stream 有什么差异？
- 如果要求消息不丢，你会怎么改？
- 为什么不把协同状态放 Redis？

追问参考回答：

- Redis Pub/Sub 和 Redis Stream 有什么差异？

  Pub/Sub 是即时广播，订阅者在线才能收到消息，消息不会为离线消费者保留。Redis Stream 是可持久化的日志结构，支持消费组、offset 和重放，更适合要求消息可靠投递的场景。当前项目用 Pub/Sub 是因为协同 update 已经落 MySQL，Redis 只负责实时通知。

- 如果要求消息不丢，你会怎么改？

  可以把广播通道从 Pub/Sub 换成 Redis Stream、Kafka 或其他消息队列。消息里带 documentId、seq 和 update，消费者按 seq 广播并记录消费位置。客户端也可以带最后应用的 seq，重连时由服务端补发缺失区间。

- 为什么不把协同状态放 Redis？

  Redis 更适合缓存和实时通道，不适合作为这个项目的最终状态库。协同文档需要长期保存、事务、外键关系、版本、评论和权限查询，MySQL 更适合做权威数据源。Redis 可以作为热点文档状态缓存，但不能替代 MySQL 持久化。

如果要求更可靠，可以用 Redis Stream、Kafka 或持久化消息队列，并让客户端带 ack 或 sequence 进行补偿。但对协同文档来说，已经有 MySQL 持久化 update，实时通道丢消息可以通过重连补齐，所以当前选择 Pub/Sub 是简单且足够的。

### Q7：owner、editor、viewer 权限模型如何落地？前端隐藏按钮够不够？

难度：中等

面试官考察点：

- 是否理解 RBAC 权限模型。
- 是否知道权限必须在服务端执行。
- 是否能区分 REST 权限和 WebSocket 权限。

参考回答：

权限模型分三类：owner 拥有完整权限，可以查看、编辑、分享、删除和恢复文档；editor 可以查看和编辑，也可以重命名、保存版本、恢复版本、更新评论状态；viewer 只能查看和接收实时更新，不能编辑、分享或删除。

前端会根据当前文档的 role 控制 UI，比如隐藏分享按钮、删除按钮、重命名表单，并把 viewer 的编辑器设置成 readOnly。但前端控制只是体验层，不能作为安全边界。

真正的权限校验都在 Java 后端。REST 接口会根据 JWT 中的 userId 查询 `document_permissions`，然后判断角色。比如删除和分享只允许 owner，重命名和版本恢复需要 owner/editor。WebSocket 连接时也会查询用户角色，并存入 session attributes；收到 `sync:update` 或 `sync:snapshot` 时再次判断 `Roles.canEdit(role)`，viewer 构造消息也会被拒绝。

可能追问：

- 如果用户打开文档后权限被 owner 改成 viewer，当前 WebSocket session 怎么处理？
- 为什么 viewer 还需要连接 WebSocket？
- 删除分享时如何避免删掉 owner？

追问参考回答：

- 如果用户打开文档后权限被 owner 改成 viewer，当前 WebSocket session 怎么处理？

  当前实现是在连接建立时查询角色并放入 session attributes，所以已建立连接的 role 不会自动变化。项目级实现可以接受这个边界，但生产方案应该在权限变更时广播权限失效事件，让客户端重连；或者服务端在每次写 update 前重新查询角色；也可以给文档权限加 revision，session 中的 revision 过期就拒绝写入。

- 为什么 viewer 还需要连接 WebSocket？

  viewer 虽然不能编辑，但仍然需要实时看到别人编辑后的内容和在线状态。如果 viewer 不连接 WebSocket，就只能靠刷新或轮询获取新内容，体验会很差。所以 viewer 可以接收 `sync:init`、`sync:update` 和 `presence:update`，但不能发送编辑类消息。

- 删除分享时如何避免删掉 owner？

  服务端删除分享时不能只相信前端按钮。仓储 SQL 会限制 `role <> 'owner'`，也就是即使有人构造删除 owner 权限的请求，数据库操作也不会删除 owner 记录。同时分享管理接口本身也要求当前用户必须是 owner。

高质量补充：

当前实现连接时固定 session role。更强的方案是在权限变更时广播权限失效事件，或每次写入前重新查询角色，或者给文档权限维护 revision，session 中保存 revision，不一致时要求重新鉴权。

### Q8：前端导入导出为什么放在浏览器侧？HTML 导入如何处理安全问题？

难度：中等

面试官考察点：

- 是否理解前后端边界。
- 是否知道后端保持格式无关的价值。
- 是否有 XSS 安全意识。

参考回答：

导入导出放在前端，是因为后端的核心职责是保存协同编辑状态，而不是理解不同文档格式。如果把 Markdown、HTML、TXT、PDF 转换放在后端，Java 服务就要引入额外格式解析、渲染和安全处理逻辑，维护成本高，也会让协同状态持久化的边界变复杂。

前端导入时会根据文件扩展名判断 Markdown、HTML 或 TXT。Markdown 通过 marked 转 HTML，TXT 转段落 HTML，HTML 会先经过白名单清洗。清洗逻辑会移除 script、style、iframe、object、embed 等危险标签，只允许有限的文本和结构标签；链接只允许 href 和 title，并拒绝 `javascript:` 和 `data:` 协议。

用户确认预览后，前端才创建新文档，并把清洗后的 HTML 作为初始内容写入 Tiptap/Yjs。导出时则从当前编辑器 HTML 转成 HTML、Markdown、TXT，PDF 使用浏览器打印流程生成。

可能追问：

- 只靠前端清洗够不够？
- PDF 为什么不是后端生成？
- 导入为什么创建新文档而不是覆盖当前文档？

追问参考回答：

- 只靠前端清洗够不够？

  只靠前端清洗不够，因为前端逻辑可以被绕过。当前项目把清洗放在前端，是因为导入是浏览器本地文件预览流程，且后端保持格式无关。生产环境还应该增加服务端安全校验、CSP、HTML 输出转义、依赖库安全升级和审计，避免恶意内容进入协作文档或导出页面。

- PDF 为什么不是后端生成？

  当前 PDF 导出使用浏览器打印流程，因为文档内容已经在前端编辑器中，样式模板也在前端，直接用浏览器渲染和打印成本最低。后端生成 PDF 需要引入 HTML 渲染引擎或无头浏览器，还要处理字体、样式和安全边界，复杂度明显增加。

- 导入为什么创建新文档而不是覆盖当前文档？

  协同文档可能已经有多人编辑和历史 update。如果导入直接覆盖当前文档，容易破坏已有 Yjs 状态，也可能误覆盖其他人的内容。创建新文档更安全，导入内容只在新文档为空时作为初始内容写入，避免破坏已有协作状态。

生产环境不能只靠前端清洗，后端或反向代理还应配合 CSP、内容安全策略和必要的服务端校验。但在这个项目的边界内，导入内容最终进入 Yjs 状态，前端预览和白名单清洗能先降低 XSS 风险。

### Q9：JWT 登录鉴权和 WebSocket 鉴权怎么做？有哪些安全取舍？

难度：中等

面试官考察点：

- 是否理解 JWT 结构、签名和过期。
- 是否知道 WebSocket 不能像普通 REST 一样每条消息带 Authorization header。
- 是否有 token 泄露风险意识。

参考回答：

REST 登录流程是用户提交 email 和 password，Java 后端根据 email 查询用户，用 BCrypt 校验密码。校验通过后，后端用 HS256 签发 JWT，payload 里包含用户 id、email 和 exp 过期时间。后续 REST 请求通过 `Authorization: Bearer <token>` 携带 token，后端校验签名和过期时间，再拿 userId 做业务权限判断。

WebSocket 鉴权发生在连接建立阶段。前端使用 WebSocket 子协议传 token，比如 `new WebSocket(url, ["bearer", token])`。Java 后端从 `Sec-WebSocket-Protocol` 中解析 bearer token，校验 JWT，然后根据 userId 和 docId 查询文档角色。token 无效返回 `UNAUTHORIZED`，没有文档权限返回 `FORBIDDEN`，并关闭连接。

安全取舍上，JWT 是无状态的，服务端不用存 session，但主动失效不方便，所以需要设置合理 TTL。项目也避免把新客户端 token 放在 URL query，因为 URL 更容易进入浏览器历史或代理日志。密码存储使用 BCrypt，而不是明文或普通哈希。

可能追问：

- JWT 被盗怎么办？
- 为什么不用 session？
- CORS 和 WebSocket Origin 怎么控制？

追问参考回答：

- JWT 被盗怎么办？

  JWT 被盗后，在过期前攻击者可能冒用用户身份，所以要降低泄露概率和缩短可用窗口。项目里通过 JWT TTL、HTTPS 部署要求、WebSocket 令牌不放 URL、严格 Origin 白名单来降低风险。生产环境还可以加入刷新令牌、令牌拒绝名单、主动退出失效、异常登录检测和密钥轮换。

- 为什么不用 session？

  Session 的优点是服务端可主动失效，适合强控制场景；缺点是多实例部署需要共享 session 或粘性会话。JWT 的优点是无状态，Java 后端可以在 REST 和 WebSocket 握手阶段用同一套签名规则校验，不依赖服务端会话存储。代价是主动失效较弱，所以需要 TTL 和额外的失效机制补充。

- CORS 和 WebSocket Origin 怎么控制？

  Java 后端通过 `ALLOWED_ORIGINS` 配置允许的前端地址。REST CORS 在 Spring MVC 配置中限制 `/api/**` 的来源、请求头和方法；WebSocket 注册 handler 时也使用同一组 allowed origins。这样可以避免任意来源页面直接调用 API 或建立 WebSocket。

高质量补充：

生产方案可以加入刷新令牌、令牌拒绝名单、退出登录失效、HTTPS、严格 CSP、密钥轮换和审计日志。CORS 和 WebSocket Origin 应通过 `ALLOWED_ORIGINS` 控制。

### Q10：如果这个系统要上线并支撑更多用户，你会优先优化哪些地方？

难度：困难

面试官考察点：

- 是否有系统演进意识。
- 是否能识别当前设计瓶颈。
- 是否知道观测、测试和架构优化的优先级。

参考回答：

我会优先从四个方向优化。

第一是 Yjs update 压缩和快照。目前文档状态依赖 update 序列重放，长期运行后打开文档会越来越慢，所以需要定期生成 snapshot，初始化时先应用 snapshot，再应用 snapshot 之后的增量，并清理旧 update。

第二是热点文档写入和广播优化。当前已经有前端 update 合并、后端按文档批量落库、出站队列和慢客户端断开，能降低数据库写入次数并隔离慢连接；但热点文档并发很高时，批量落库耗时、单文档广播扇出和出站队列仍会成为瓶颈。可以继续调优批量窗口、限制 update 大小和频率，或者拆出专门的协同同步服务。

第三是可观测性。已有 HTTP 请求数、WebSocket 连接数和消息数，但还可以补充 update 写入耗时、广播耗时、Redis 发布失败数、WebSocket 错误码统计、文档初始化 update 数量、snapshot 命中率等指标。

第四是契约和测试。后续应该补充契约测试，确保 REST 字段、错误格式、WebSocket 消息、角色语义、前端类型和 Java 实现不分叉。

可能追问：

- 你会先做快照还是先做分布式扩容？
- 如何设计 snapshot 的触发条件？
- 如何验证优化真的有效？

追问参考回答：

- 你会先做快照还是先做分布式扩容？

  我会先看指标。如果瓶颈是大文档打开慢、`sync:init` updates 数量很大，就先做快照；如果瓶颈是连接数、CPU 或单实例内存，就先做 WebSocket 水平扩容；如果瓶颈是 `persist_duration` 或批量大小异常，就调优批量窗口和数据库连接池；如果瓶颈是队列深度或 `SLOW_CLIENT`，就处理出站广播和慢客户端。没有数据前不直接做大架构调整。

- 如何设计 snapshot 的触发条件？

  可以按 update 数量、累计 update 字节数、文档打开耗时或定时任务触发。比如某文档增量超过 100 条或累计超过一定大小时，由有编辑权限的客户端或后端任务生成 Yjs snapshot，服务端保存 snapshotSeq，并删除 snapshotSeq 之前的旧 update。关键是 snapshotSeq 要作为事务边界，确保压缩期间新增 update 不丢。

- 如何验证优化真的有效？

  要先定义指标，例如文档初始化耗时、`sync:init` payload 大小、单文档 update 数量、数据库查询耗时、WebSocket 连接数、写锁等待时间和错误率。优化前后用同一批压测数据对比，如果初始化耗时下降、payload 变小、错误率不升高，才能说明优化有效。

高质量补充：

我会先用指标定位瓶颈，而不是直接改架构。如果数据显示大文档初始化慢，就先做 snapshot；如果是 WebSocket 连接数压力，就先做水平扩容和压测；如果是批量落库耗时高，就调 `WS_BATCH_MAX_SIZE`、`WS_BATCH_FLUSH_MS` 和连接池；如果是队列深度或慢客户端高，就优化出站广播和限流策略。

### 面试官评分参考

强回答通常具备这些特征：

- 能把业务目标、技术方案和工程权衡讲完整。
- 能讲清前端、Java 后端、MySQL、Redis、WebSocket 的职责边界。
- 能解释为什么用 Yjs，而不是自己合并文本。
- 能说出 MySQL 事务、`document_sequences` 和唯一约束如何保证 update seq。
- 能区分 Redis 实时广播和 MySQL 最终状态来源。
- 能说明权限不仅在前端控制，也在 REST 和 WebSocket 后端校验。
- 能主动提到安全、测试、可观测性和后续演进。

弱回答常见问题：

- 只会说“用了 WebSocket 实现实时通信”，讲不出消息类型和持久化流程。
- 只会说“用了 Redis”，讲不清为什么需要 Redis，以及 Redis 丢消息怎么办。
- 把 Yjs update 说成普通文本或 HTML。
- 把权限控制停留在前端按钮隐藏。
- 讲不清版本恢复后为什么要让客户端重新加载。
- 讲不清前端和 Java 后端为什么需要契约对齐。

### 30 秒项目回答模板

```text
这个项目是一个在线文档协同编辑系统，前端用 React、Tiptap 和 Yjs 实现富文本协同编辑，Java 后端用 Spring Boot 提供 REST API 和 WebSocket 实时同步。用户编辑时前端产生 Yjs update，通过 WebSocket 发给后端，后端校验 JWT 和文档权限后写入 MySQL，再广播给其他客户端，多实例下通过 Redis Pub/Sub 转发。系统还实现了 owner/editor/viewer 权限、分享、评论、版本保存恢复、回收站和导入导出。我的重点工作是前后端核心链路、契约维护、Yjs update 持久化、WebSocket 同步和权限安全。
```

### 2 分钟项目回答模板

```text
这个项目面向在线协同办公场景，目标是支持多人同时编辑同一篇富文本文档。前端使用 React 19、TypeScript、Vite、Tiptap 和 Yjs，Tiptap 负责编辑器体验，Yjs 负责协同状态合并。Java 后端使用 Java 21 和 Spring Boot 3，对外提供统一 REST API 和 WebSocket 协议，数据层使用 MySQL，跨实例广播使用 Redis Pub/Sub。

用户登录后拿到 JWT，后续 REST 请求通过 Bearer 令牌鉴权。打开文档时，前端建立 WebSocket 连接并通过子协议传令牌。Java 后端校验令牌和用户在文档中的 owner/editor/viewer 角色，加载 MySQL 中的 snapshot 和 Yjs updates，通过 sync:init 初始化客户端。用户编辑时，前端把本地 Yjs update 编码成 Base64 发送 sync:update，后端校验权限后写入 document_updates，并通过本地 WebSocket session 和 Redis Pub/Sub 广播给其他在线用户。

项目还支持文档分享授权、评论回复、版本保存与恢复、软删除恢复和导入导出。我设计并维护 OpenAPI、WebSocket 消息协议和 MySQL Schema 作为前端与 Java 后端的契约，避免接口字段和业务语义分叉。这个项目的难点主要是实时协同链路、权限边界、并发写入顺序和多实例广播一致性。
```

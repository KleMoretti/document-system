# Java 全栈项目面试官提问与参考回答

本文基于简历项目“在线文档协同编辑系统”整理，视角是 Java 全栈面试官会如何根据项目描述继续深挖，以及候选人应该如何回答。适用于 Java 全栈开发、Java 后端开发、协同办公/实时系统方向面试。

## 0. 面试官会先看什么

简历里最容易引发追问的关键词：

- React 19、TypeScript、Vite、Tiptap、Yjs
- Java 21、Spring Boot 3、WebSocket
- MySQL、Redis、JWT
- 在线文档协同编辑、实时协作、分享授权、评论回复、版本保存与恢复
- 前后端统一契约、OpenAPI、WebSocket 消息协议、MySQL Schema
- Java/Go 后端可切换

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

## Q1：请你用 2 分钟介绍一下这个项目，你主要负责什么？

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

我主要负责前后端核心功能开发，包括前端协同编辑器接入、Java 后端认证授权、文档权限模型、Yjs update 持久化、WebSocket 初始化同步和广播，以及 OpenAPI、WebSocket 协议和 SQL Schema 的统一契约维护。项目亮点是前端只依赖统一契约，可以切换 Java 或 Go 后端；同时用 MySQL 作为最终状态来源，用 Redis 做低延迟跨实例广播。

高质量加分点：

- 明确说出“Yjs 负责协同冲突合并，服务端不解析富文本语义”。
- 明确说出“REST 管业务资源，WebSocket 管实时增量”。
- 明确说出“前端后端无关，靠共享契约避免 Java/Go 语义分叉”。

可能追问：

- 项目里最难的点是什么？
- 你具体写了哪些 Java 类或前端组件？
- 如果没有 Go 后端，为什么还要强调统一契约？

追问参考回答：

- 项目里最难的点是什么？

  最难的是实时协同链路的边界划分和一致性保证。协同编辑涉及前端编辑器、Yjs 状态、WebSocket 传输、MySQL 持久化和 Redis 多实例广播，如果边界不清晰，后端可能会承担富文本冲突合并，复杂度会很高。我的处理方式是让 Yjs 负责冲突合并，Java 后端只负责鉴权、权限、update 持久化和广播，并用 MySQL 作为最终状态来源。

- 你具体写了哪些 Java 类或前端组件？

  前端主要是 `App.tsx`、`CollaborativeEditor.tsx`、`api.ts`、`documentFormats.ts` 和 `exportStyles.ts` 相关能力；Java 后端主要是 `AuthController`、`DocumentController`、`DocumentSocketHandler`、`AppRepository`、`JwtManager`、`Roles`、`RedisBus` 这些核心类。对应模块覆盖登录鉴权、文档 REST API、WebSocket 同步、Yjs update 落库、权限控制和跨实例广播。

- 如果没有 Go 后端，为什么还要强调统一契约？

  即使只有一个后端，契约也能让前后端边界稳定，降低联调成本。这个项目里还有 Go 后端，所以契约更重要：REST 字段、WebSocket 消息、错误格式、权限语义和 SQL schema 都要以共享契约为准，前端不能适配某个后端私有行为，否则后端切换就会出问题。

## Q2：为什么这个项目需要 Yjs？为什么不自己处理多人编辑冲突？

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

## Q3：一次实时编辑从前端到 Java 后端再到其他用户，完整链路是什么？

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

## Q4：Java 后端如何保证同一文档的 update 序号不冲突？

难度：困难

面试官考察点：

- 是否理解数据库事务和行锁。
- 是否知道协同编辑写路径中的并发风险。
- 是否能讲出唯一约束兜底。

参考回答：

风险点在于多个用户可能同时编辑同一篇文档，多个 WebSocket 消息几乎同时到达后端。如果服务端只是先查最大 seq 再加 1，两个事务可能读到相同最大值，导致插入相同 seq。

Java 后端在追加 update 时使用事务，并先对文档行执行 `SELECT id FROM documents WHERE id = ? FOR UPDATE`。这个行锁会把同一文档的并发写入串行化。拿到锁以后，再查询 `document_updates` 最大 seq 和 `document_snapshots` 最大 last_seq，取较大值加 1 作为新 seq，然后插入 update。

数据库层面，`document_updates` 上还有 `(document_id, seq)` 唯一约束，作为最后兜底。这样同一篇文档的 update 序号是唯一递增的，不同文档之间因为锁的是不同 document 行，不会互相阻塞。

可能追问：

- `FOR UPDATE` 不放在事务里会怎样？
- 为什么要同时看 snapshots 的 last_seq？
- 热点文档并发很高时这个设计有什么瓶颈？

追问参考回答：

- `FOR UPDATE` 不放在事务里会怎样？

  `FOR UPDATE` 的锁生命周期依赖事务。如果没有事务，数据库执行完这条语句后锁很快释放，后续查询最大 seq 和插入 update 就不再受锁保护，仍然可能出现并发事务读到相同 seq 的问题。所以 append update 必须放在事务里，让锁覆盖“取序号 + 插入 update”的完整过程。

- 为什么要同时看 snapshots 的 last_seq？

  因为引入快照后，`document_updates` 中一部分旧 update 会被删除，但它们已经被 snapshot 覆盖。如果新 seq 只看 updates 表的最大 seq，可能会回退到小于等于 snapshotSeq 的序号。取 updates 最大 seq 和 snapshots 最大 last_seq 的较大值再加 1，可以保证序号持续递增。

- 热点文档并发很高时这个设计有什么瓶颈？

  同一文档的写入会被行锁串行化，热点文档编辑人数很多时，锁等待会增加，写入延迟会上升。优化方向可以是客户端合并 update、服务端批量写入、按文档建立写队列、提高 snapshot 压缩频率，或者把协同同步独立成专门服务。

这个设计牺牲了同一文档写入的并行度，换来序列一致性。对于热点文档，瓶颈会出现在同文档写入串行化上，可以考虑写入队列、批量合并 update、快照压缩、限制单文档并发编辑人数或做更细粒度的协同服务拆分。

## Q5：版本保存和版本恢复是怎么实现的？为什么恢复后要让前端重新加载？

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

## Q6：Redis Pub/Sub 在项目中解决什么问题？如果 Redis 丢消息怎么办？

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

## Q7：owner、editor、viewer 权限模型如何落地？前端隐藏按钮够不够？

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

## Q8：前端导入导出为什么放在浏览器侧？HTML 导入如何处理安全问题？

难度：中等

面试官考察点：

- 是否理解前后端边界。
- 是否知道后端保持格式无关的价值。
- 是否有 XSS 安全意识。

参考回答：

导入导出放在前端，是因为后端的核心职责是保存协同编辑状态，而不是理解不同文档格式。项目中 Java 和 Go 后端需要保持统一语义，如果把 Markdown、HTML、TXT、PDF 转换放在后端，就要两套后端都实现相同格式逻辑，维护成本高，也容易出现差异。

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

  当前 PDF 导出是浏览器 print flow，因为文档内容已经在前端编辑器中，样式模板也在前端，直接用浏览器渲染和打印成本最低。后端生成 PDF 需要引入 HTML 渲染引擎或 headless browser，还要保证 Java/Go 两套后端行为一致，复杂度明显增加。

- 导入为什么创建新文档而不是覆盖当前文档？

  协同文档可能已经有多人编辑和历史 update。如果导入直接覆盖当前文档，容易破坏已有 Yjs 状态，也可能误覆盖其他人的内容。创建新文档更安全，导入内容只在新文档为空时作为初始内容写入，避免破坏已有协作状态。

生产环境不能只靠前端清洗，后端或反向代理还应配合 CSP、内容安全策略和必要的服务端校验。但在这个项目的边界内，导入内容最终进入 Yjs 状态，前端预览和白名单清洗能先降低 XSS 风险。

## Q9：JWT 登录鉴权和 WebSocket 鉴权怎么做？有哪些安全取舍？

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

  JWT 被盗后，在过期前攻击者可能冒用用户身份，所以要降低泄露概率和缩短可用窗口。项目里通过 JWT TTL、HTTPS 部署要求、WebSocket token 不放 URL、严格 Origin 白名单来降低风险。生产环境还可以加入 refresh token、token denylist、主动退出失效、异常登录检测和密钥轮换。

- 为什么不用 session？

  Session 的优点是服务端可主动失效，适合强控制场景；缺点是多实例部署需要共享 session 或粘性会话。JWT 的优点是无状态，Java 和 Go 后端都能用同一套签名规则校验，更适合当前前端可切换后端的设计。代价是主动失效较弱，所以需要 TTL 和额外的失效机制补充。

- CORS 和 WebSocket Origin 怎么控制？

  Java 后端通过 `ALLOWED_ORIGINS` 配置允许的前端地址。REST CORS 在 Spring MVC 配置中限制 `/api/**` 的来源、请求头和方法；WebSocket 注册 handler 时也使用同一组 allowed origins。这样可以避免任意来源页面直接调用 API 或建立 WebSocket。

高质量补充：

生产方案可以加入 refresh token、token denylist、退出登录失效、HTTPS、严格 CSP、密钥轮换和审计日志。CORS 和 WebSocket Origin 应通过 `ALLOWED_ORIGINS` 控制。

## Q10：如果这个系统要上线并支撑更多用户，你会优先优化哪些地方？

难度：困难

面试官考察点：

- 是否有系统演进意识。
- 是否能识别当前设计瓶颈。
- 是否知道观测、测试和架构优化的优先级。

参考回答：

我会优先从四个方向优化。

第一是 Yjs update 压缩和快照。目前文档状态依赖 update 序列重放，长期运行后打开文档会越来越慢，所以需要定期生成 snapshot，初始化时先应用 snapshot，再应用 snapshot 之后的增量，并清理旧 update。

第二是热点文档写入优化。当前同一文档 update 写入通过行锁串行化，能保证 seq 一致，但热点文档并发很高时会成为瓶颈。可以考虑批量合并 update、单文档写队列、限制 update 大小和频率，或者拆出专门的协同同步服务。

第三是可观测性。已有 HTTP 请求数、WebSocket 连接数和消息数，但还可以补充 update 写入耗时、广播耗时、Redis 发布失败数、WebSocket 错误码统计、文档初始化 update 数量、snapshot 命中率等指标。

第四是契约和测试。因为前端要同时适配 Java/Go 后端，后续应该补充契约测试，确保 REST 字段、错误格式、WebSocket 消息和角色语义不分叉。

可能追问：

- 你会先做快照还是先做分布式扩容？
- 如何设计 snapshot 的触发条件？
- 如何验证优化真的有效？

追问参考回答：

- 你会先做快照还是先做分布式扩容？

  我会先看指标。如果瓶颈是大文档打开慢、`sync:init` updates 数量很大，就先做快照；如果瓶颈是连接数、CPU 或单实例内存，就先做 WebSocket 水平扩容；如果瓶颈是同一文档写锁等待，就优化写入模型。没有数据前不直接做大架构调整。

- 如何设计 snapshot 的触发条件？

  可以按 update 数量、累计 update 字节数、文档打开耗时或定时任务触发。比如某文档增量超过 100 条或累计超过一定大小时，由有编辑权限的客户端或后端任务生成 Yjs snapshot，服务端保存 snapshotSeq，并删除 snapshotSeq 之前的旧 update。关键是 snapshotSeq 要作为事务边界，确保压缩期间新增 update 不丢。

- 如何验证优化真的有效？

  要先定义指标，例如文档初始化耗时、`sync:init` payload 大小、单文档 update 数量、数据库查询耗时、WebSocket 连接数、写锁等待时间和错误率。优化前后用同一批压测数据对比，如果初始化耗时下降、payload 变小、错误率不升高，才能说明优化有效。

高质量补充：

我会先用指标定位瓶颈，而不是直接改架构。如果数据显示大文档初始化慢，就先做 snapshot；如果是 WebSocket 连接数压力，就先做水平扩容和压测；如果是同一文档写锁等待高，再考虑写入模型优化。

## 面试官评分参考

强回答通常具备这些特征：

- 能把业务目标、技术方案和工程权衡讲完整。
- 能讲清前端、Java 后端、MySQL、Redis、WebSocket 的职责边界。
- 能解释为什么用 Yjs，而不是自己合并文本。
- 能说出 MySQL 事务和行锁如何保证 update seq。
- 能区分 Redis 实时广播和 MySQL 最终状态来源。
- 能说明权限不仅在前端控制，也在 REST 和 WebSocket 后端校验。
- 能主动提到安全、测试、可观测性和后续演进。

弱回答常见问题：

- 只会说“用了 WebSocket 实现实时通信”，讲不出消息类型和持久化流程。
- 只会说“用了 Redis”，讲不清为什么需要 Redis，以及 Redis 丢消息怎么办。
- 把 Yjs update 说成普通文本或 HTML。
- 把权限控制停留在前端按钮隐藏。
- 讲不清版本恢复后为什么要让客户端重新加载。
- 讲不清 Java 和 Go 后端为什么需要统一契约。

## 30 秒项目回答模板

```text
这个项目是一个在线文档协同编辑系统，前端用 React、Tiptap 和 Yjs 实现富文本协同编辑，Java 后端用 Spring Boot 提供 REST API 和 WebSocket 实时同步。用户编辑时前端产生 Yjs update，通过 WebSocket 发给后端，后端校验 JWT 和文档权限后写入 MySQL，再广播给其他客户端，多实例下通过 Redis Pub/Sub 转发。系统还实现了 owner/editor/viewer 权限、分享、评论、版本保存恢复、回收站和导入导出。我的重点工作是前后端核心链路、统一契约、Yjs update 持久化、WebSocket 同步和权限安全。
```

## 2 分钟项目回答模板

```text
这个项目面向在线协同办公场景，目标是支持多人同时编辑同一篇富文本文档。前端使用 React 19、TypeScript、Vite、Tiptap 和 Yjs，Tiptap 负责编辑器体验，Yjs 负责协同状态合并。Java 后端使用 Java 21 和 Spring Boot 3，对外提供统一 REST API 和 WebSocket 协议，数据层使用 MySQL，跨实例广播使用 Redis Pub/Sub。

用户登录后拿到 JWT，后续 REST 请求通过 Bearer Token 鉴权。打开文档时，前端建立 WebSocket 连接并通过子协议传 token。Java 后端校验 token 和用户在文档中的 owner/editor/viewer 角色，加载 MySQL 中的 snapshot 和 Yjs updates，通过 sync:init 初始化客户端。用户编辑时，前端把本地 Yjs update 编码成 Base64 发送 sync:update，后端校验权限后写入 document_updates，并通过本地 WebSocket session 和 Redis Pub/Sub 广播给其他在线用户。

项目还支持文档分享授权、评论回复、版本保存与恢复、软删除恢复和导入导出。为了保证前端可以切换 Java/Go 后端，我设计并维护了 OpenAPI、WebSocket 消息协议和 MySQL Schema 作为统一契约，避免接口字段和业务语义分叉。这个项目的难点主要是实时协同链路、权限边界、并发写入顺序和多实例广播一致性。
```

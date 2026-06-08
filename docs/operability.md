# 运维与压测说明

本说明按当前 Java 后端实现整理，记录 CI、可观测性、健康检查、WebSocket 压测和高并发参数。

## CI 流水线

GitHub Actions 工作流：`.github/workflows/ci.yml`

当前工作流会执行：

- Web 单元测试和生产构建。
- Java Maven 测试。
- Java 后端和连接 Java 后端的 Web 镜像构建。
- 仓库中的其他模块检查不作为本文 Java 实现和压测结论的依据。

## Java 指标

Java 后端在以下路径暴露 Prometheus 兼容指标：

```text
GET /metrics
```

## Java 健康检查

Java 后端提供：

```text
GET /healthz
GET /readyz
```

`/healthz` 用于确认进程存活。`/readyz` 执行 `SELECT 1` 检查当前服务角色对应的数据库；失败时返回 HTTP 503 和 `{ "status": "not_ready" }`。auth 服务检查 `documentation_auth` 库，document 和 realtime 服务检查 `documentation_collab` 库，all 单体模式检查统合库。

## 微服务拆分部署

Java 后端支持通过 `APP_SERVICE_ROLE` 环境变量按角色拆分部署。Docker Compose 中提供 `split` profile 用于启动三服务拓扑：

```powershell
# 启动基础设施
docker compose up -d mysql redis

# 启动拆分的三个 Java 服务
docker compose --profile split up -d backend-java-auth backend-java-document backend-java-realtime
```

三个服务的环境变量配置：

| 服务 | 端口映射 | 数据库 | Redis | 额外配置 |
|------|---------|--------|-------|---------|
| backend-java-auth | 18082:8080 | documentation_auth | 不需要 | SERVICE_TOKEN |
| backend-java-document | 18083:8080 | documentation_collab | publish | AUTH_BASE_URL, SERVICE_TOKEN |
| backend-java-realtime | 18084:8080 | documentation_collab | subscribe | — |

关键配置项：

- `SERVICE_TOKEN`：内部接口的共享密钥，auth 和 document 服务必须一致。
- `AUTH_BASE_URL`：document 服务访问 auth 内部接口的基地址。
- `APP_SERVICE_ROLE`：服务角色，取值为 `auth`、`document`、`realtime` 或 `all`（默认）。

当前指标包括：

- `documentation_collab_http_requests_total{method,path,status}`
- `documentation_collab_ws_connections_total`
- `documentation_collab_ws_connections_active`
- `documentation_collab_ws_messages_total{type}`
- `documentation_collab_ws_errors_total{code}`
- `documentation_collab_ws_message_bytes_total`
- `documentation_collab_ws_slow_clients_total`
- `documentation_collab_ws_send_queue_depth_max`
- `documentation_collab_ws_broadcast_duration_ms_count|sum|max`
- `documentation_collab_ws_persist_duration_ms_count|sum|max`
- `documentation_collab_ws_batch_size_count|sum|max`

本地检查示例：

```powershell
Invoke-WebRequest http://localhost:18080/metrics -UseBasicParsing
```

## WebSocket 压测

压测工具当前位于 `apps/backend-go/cmd/ws-loadtest`，但目标地址可以直接指向 Java WebSocket。工具通过 WebSocket 子协议传递 JWT，不把令牌放入 URL。

```powershell
npm run loadtest:ws -- -url ws://localhost:18080/ws/documents -doc-id <uuid> -token <jwt> -clients 100 -duration 30s -interval 1s -mode presence
```

如需测试写入链路，使用带 `owner` 或 `editor` 权限的 token，并指定 `-mode update`：

```powershell
npm run loadtest:ws -- -url ws://localhost:18080/ws/documents -doc-id <uuid> -token <jwt> -clients 100 -duration 30s -interval 1s -mode update
```

压测命令通过 WebSocket `Sec-WebSocket-Protocol` 请求头（`bearer, <jwt>`）传递 JWT，不把令牌放进 URL 查询参数。命令会输出 JSON 报告，包括连接数、消息数、错误数、错误阶段、服务端错误码、写调用延迟分位数，以及从回显 update / presence 时间戳推导出的接收延迟分位数。

重要输出字段：

- `clients`：请求启动的客户端数量。
- `connected`：完成 WebSocket 握手并收到 `sync:init` 的客户端数量。
- `sent`：压测客户端成功写出的消息数。
- `received`：所有压测客户端累计读取到的消息数。
- `errors`：客户端侧 dial、init、read 或 write 错误数。
- `disconnects`：压测截止时间前发生的读取侧断连数。
- `error_codes`：客户端观察到的服务端 `error.code` 计数，例如 `SLOW_CLIENT`。
- `error_stages`：客户端侧失败阶段，目前包括 `dial`、`init`、`read`、`write`。
- `latency_*`：本地 WebSocket 写调用延迟分位数，不代表持久化延迟。
- `receive_latency_*`：根据回显压测消息内嵌时间戳推导出的端到端接收延迟。

`presence` 模式用于压测不经过 MySQL 写入的 Java 本地广播和 Redis 发布路径。`update` 模式用于压测完整编辑链路：WebSocket 接收、权限校验、Base64 解码、1 MiB 大小限制、`UpdateBatcher` 按文档批量持久化、Redis / 本地广播，以及出站队列投递。当每个客户端每秒都发送 presence 时，presence 压测会比真实光标流量更严苛，因为每条消息都会扇出给同一文档的所有活跃连接。

每次做可对比压测前，应重启目标后端，或记录压测前 `/metrics` 的基线值，避免累计计数器影响结果解读。Java 后端本地 Docker 压测的典型启动方式如下：

```powershell
docker compose up -d mysql redis
docker run -d --name documentation-collab-backend-java --network documentation_default -p 18080:8080 `
  -e MYSQL_HOST=mysql -e MYSQL_PASSWORD=root `
  -e REDIS_HOST=redis `
  -e JWT_SECRET=local-documentation-secret-please-change `
  -e WS_SEND_QUEUE_SIZE=128 `
  documentation-backend-java:loadtest
```

本地验证中使用的 `documentation-backend-java:loadtest` 是基于当前 Spring Boot jar 构建的临时镜像，不属于已提交的 Docker Compose 契约。

## 高并发调优项

Java 后端对热点协同编辑支持以下环境变量：

```text
DB_MAX_OPEN_CONNS=50
DB_MAX_IDLE_CONNS=25
WS_SEND_QUEUE_SIZE=32
WS_BATCH_MAX_SIZE=32
WS_BATCH_FLUSH_MS=25
WS_SNAPSHOT_MIN_UPDATES=100
```

`WS_SEND_QUEUE_SIZE` 控制每个 WebSocket 连接的出站队列上限。当某个客户端消费过慢、队列被填满时，后端会发送 `SLOW_CLIENT` 并关闭该连接，避免一个慢浏览器阻塞整篇文档的广播路径。

`WS_BATCH_MAX_SIZE` 和 `WS_BATCH_FLUSH_MS` 控制 `sync:update` 的短周期批量持久化。默认达到 32 条或等待 25 ms 后触发一次批量落库。后端仍然只在 MySQL 持久化成功后广播，但同一文档的并发 update 可以共享一次事务和一段连续 seq 分配。落库序号由 `document_sequences.next_seq` 分配，避免热点文档每次写入都扫描 `document_updates` 最大序号。

`WS_SNAPSHOT_MIN_UPDATES` 用于避免多个编辑者过于频繁地压缩同一文档。只有当 `snapshotSeq == 当前快照序号 + 当前未压缩增量数`，并且当前未压缩增量数达到阈值时，服务端才接受快照。

前端也有固定的写入削峰策略：本地 Yjs update 会在 35 ms 窗口内合并；当 WebSocket 发送缓冲超过 1 MiB 时，关键 `sync:update` 每 50 ms 重试，非关键 `presence:update` 直接跳过。这些是前端代码常量，不是后端环境变量。

## 压测设计与结果解读

修改协同编辑链路后，至少运行以下场景：

- `presence` 冒烟：100 客户端，20-30 秒，1 秒发送间隔。
- `update` 冒烟：100 客户端，10-30 秒，1 秒发送间隔。
- `update` 容量检查：逐步提高客户端数量，直到连接成功率、`errors`、`disconnects`、接收 P95、`SLOW_CLIENT` 和后端队列深度显示出本地容量边界。
- `presence` 容量检查：需要与 update 结果分开解读，因为它测的是纯广播扇出压力，可能更早触发瓶颈。

在本地单后端环境中，一次健康压测通常应满足：

- `connected` 等于 `clients`。
- 冒烟压测中的 `errors` 和 `disconnects` 为 0。
- 命令退出后，`documentation_collab_ws_connections_active` 回到 0。
- 目标场景下，`documentation_collab_ws_slow_clients_total` 保持为 0。
- `documentation_collab_ws_send_queue_depth_max` 低于 `WS_SEND_QUEUE_SIZE`；如果达到队列上限，说明至少有一个客户端已经跟不上广播速度。
- 在 `update` 模式下，`documentation_collab_ws_batch_size_sum` 应与持久化 update 数一致，`_count` 表示实际触发了多少次 MySQL 批量落库。
- `documentation_collab_ws_persist_duration_ms_*` 反映批量落库耗时，`documentation_collab_ws_broadcast_duration_ms_*` 反映本地广播入队耗时；两者需要分开看，前者偏数据库瓶颈，后者偏单文档连接数和慢客户端瓶颈。

最近一次 Java 后端本地 Docker 验证使用 MySQL 8.4、Redis 8、`documentation-backend-java:loadtest`，并将 `WS_SEND_QUEUE_SIZE` 调整为 128：

```text
模式: update
客户端数: 300
持续时间: 20s
连接成功: 300/300
发送消息: 5600
接收消息: 1680000
错误数: 0
断连数: 0
接收 P95: 188.9312ms
后端批处理: 5600 个 update 合并为 232 次批量落库
慢客户端: 0
最大队列深度: 38
```

```text
模式: presence
客户端数: 100
持续时间: 20s
连接成功: 100/100
发送消息: 1900
接收消息: 190000
错误数: 0
断连数: 0
接收 P95: 33.6942ms
慢客户端: 0
最大队列深度: 92
```

同一套本地 Docker 环境下，即使 `WS_SEND_QUEUE_SIZE=128`，200-500 客户端的 `presence` 压测仍无法健康通过。这些压测会填满出站队列并产生 `SLOW_CLIENT` 断连。这个结果应理解为“所有客户端每秒发送 presence”这一合成场景下的当前单节点扇出边界，而不是少数用户真实编辑时的容量上限。

## 压测报告模板

记录压测结果时使用以下格式：

```text
日期:
后端: Java
Commit:
环境:
客户端数:
持续时间:
模式: presence | update
连接成功数:
发送消息数:
接收消息数:
错误数:
断连数:
错误码:
错误阶段:
P50:
P95:
Max:
压测结束后后端活跃 WS 连接数:
后端慢客户端数:
后端最大队列深度:
后端批处理次数:
后端最大批大小:
备注:
```

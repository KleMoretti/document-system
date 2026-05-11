# AGENTS.md

本文件定义本仓库内智能体的默认协作规则。除非用户明确覆盖，否则在阅读、修改、验证和提交本项目时遵循以下约定。

## 1. 仓库定位

这是一个在线文档协同编辑系统的 monorepo，包含一个前端和两套后端实现：

- `apps/web`：React + TypeScript + Vite 前端
- `apps/backend-java`：Java 21 + Spring Boot 后端
- `apps/backend-go`：Go 后端
- `packages/shared-contract`：REST、WebSocket、SQL 契约
- `docs`：架构与接口说明
- `infra`：基础设施辅助文件

前端必须保持后端无关性；Java 和 Go 后端必须对外暴露相同的 REST 与 WebSocket 能力。

## 2. 工作原则

- 先读再改。先查看相关目录、契约文档和现有实现，再决定修改位置。
- 只做与当前任务直接相关的改动，不顺手重构无关模块。
- 优先保持前后端契约一致性，不要只改一端。
- 优先做小而清晰的改动；如果需要大改，先向用户说明拆分方案。
- 不提交密钥、密码、本机路径或仅本机可用的临时文件。
- 不覆盖用户已有改动；发现与当前任务冲突的未提交修改时，先停下来确认。

## 3. 目录与改动边界

### 前端

- 前端入口在 `apps/web/src`。
- 前端通过统一 API / WebSocket 契约连接后端，不要把某个后端的私有行为写死到界面逻辑里。
- 环境切换由前端 `.env.local` 负责；不要把临时联调配置写进受版本控制的源码。

### Java / Go 后端

- 两套后端是同一产品语义的两种实现，不是两条独立业务线。
- 如果修改认证、角色、文档同步、错误格式、WebSocket 消息结构，默认需要检查另一套后端是否也要同步更新。
- 新增或修改接口时，优先以共享契约为准，而不是让前端适配某一端的特例。

### 共享契约

- `packages/shared-contract/openapi.yml`、`packages/shared-contract/websocket.md`、`packages/shared-contract/sql/schema.mysql.sql` 是跨端事实来源。
- 若变更接口、消息字段、角色语义或持久化结构，默认同时更新相应契约文档。
- `docs/api-contract.md` 和 `docs/architecture.md` 用于补充说明，不能长期落后于实现。

## 4. 修改前后的检查清单

开始前至少确认：

- 需求影响前端、Java 后端、Go 后端中的哪些部分
- 是否触及共享契约
- 是否需要更新测试或文档

完成后至少验证：

- 受影响子系统的测试已运行，或明确说明未运行原因
- 没有把构建产物、缓存、日志或本机临时文件加入版本控制
- 若改动 API / WebSocket / schema，相关文档已同步

## 5. 常用命令

在仓库根目录优先使用这些命令：

```powershell
npm run test:web
npm run build:web
npm run test:go
npm run test:java
```

需要单独启动服务时，可使用：

```powershell
npm run dev:web
npm run dev:go
npm run dev:java
```

机器专属环境准备、数据库口令、Redis/MySQL 启动方式以 `README.md` 为准；`AGENTS.md` 不重复保存这些本机细节。

## 6. 提交与文档约定

- 提交前优先检查 `git status`，确认只包含本任务相关文件。
- 修改行为如果会影响协作协议、接口或运行方式，补充更新 `docs/` 或 `packages/shared-contract/`。
- 新增说明时优先写在现有文档体系中，避免把运行说明散落到多个重复文件。

## 7. 明确禁止

- 不要提交 `node_modules`、日志、缓存、构建输出或临时备份文件。
- 不要在未确认的情况下删除用户文件、重置分支或回退现有改动。
- 不要引入只对单一后端成立、但会破坏“前端可切换后端”前提的改动。
- 不要让 Java 和 Go 后端在同一业务能力上出现不同的请求/响应字段语义，除非用户明确要求分叉。

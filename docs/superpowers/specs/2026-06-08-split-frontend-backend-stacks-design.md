# 拆分前端 + Java / 前端 + Go 双项目设计

## 目标

将当前 `apps/web + apps/backend-java + apps/backend-go + packages/shared-contract` 的 monorepo 物理拆成两个可独立演进的项目：

- `frontend-java`：一份前端 + Java 后端 + 独立共享契约。
- `frontend-go`：一份前端 + Go 后端 + 独立共享契约。

拆分后，用户可以只修改 Java 栈或只修改 Go 栈，不再被要求同时维护另一套后端行为一致性。

## 当前状态

当前仓库以同一份 React 前端连接 Java 或 Go 后端，两套后端共享 `packages/shared-contract` 中的 REST、WebSocket 和 SQL 契约。根 `package.json`、`.github/workflows/ci.yml` 和 `docker-compose.yml` 都假设这是一个共享前端、双后端的单一产品。

这个结构适合契约一致性练习，但不适合独立修改任意一种后端：修改 Java 或 Go 后端时，文档、CI 和契约都会默认要求另一套实现同步。

## 目标目录结构

```text
frontend-java/
  web/
  backend-java/
  shared-contract/
  docker-compose.yml
  package.json
  README.md

frontend-go/
  web/
  backend-go/
  shared-contract/
  docker-compose.yml
  package.json
  README.md
```

根目录保留为聚合壳，主要放仓库级说明、文档和可选聚合脚本。拆分后不再保留 `apps/web`、`apps/backend-java`、`apps/backend-go`、`packages/shared-contract` 作为主开发入口。

## 复制与迁移规则

- `apps/web` 复制为 `frontend-java/web` 和 `frontend-go/web`。
- `apps/backend-java` 移动到 `frontend-java/backend-java`。
- `apps/backend-go` 移动到 `frontend-go/backend-go`。
- `packages/shared-contract` 复制为两个项目内的 `shared-contract`。
- 两份 `shared-contract` 是初始拷贝，后续允许独立修改。
- 前端源码在两个项目中各自维护；之后通用 UI 改动需要手动同步，除非后续重新引入共享包。

## 脚本设计

每个项目有自己的 `package.json`：

`frontend-java/package.json`：

- `dev:web`
- `build:web`
- `test:web`
- `dev:backend`
- `test:backend`

`frontend-go/package.json`：

- `dev:web`
- `build:web`
- `test:web`
- `dev:backend`
- `test:backend`
- `loadtest:ws`

根 `package.json` 保留聚合命令：

- `test:java-stack`
- `test:go-stack`
- `build:java-stack`
- `build:go-stack`

根命令只作为便利入口，不再表达“两个后端必须一起改”的规则。

## Docker Compose 设计

每个项目各自维护 `docker-compose.yml`：

- `frontend-java/docker-compose.yml` 启动 `mysql`、`redis`、`backend-java`、`web`。
- `frontend-go/docker-compose.yml` 启动 `mysql`、`redis`、`backend-go`、`web`。

两个 compose 文件都从本项目内的 `shared-contract/sql/schema.mysql.sql` 初始化数据库。端口可以沿用当前默认值，也可以为避免并行启动冲突做区分：

- Java 栈默认使用 `18080` 和 `15173`。
- Go 栈默认使用 `18081` 和 `15174`。

## CI 设计

CI 改成两个独立栈：

- Java 栈：安装 Node，测试和构建 `frontend-java/web`；安装 Java 21，执行 `frontend-java/backend-java` 的 Maven 测试；构建 Java 栈 Docker 镜像。
- Go 栈：安装 Node，测试和构建 `frontend-go/web`；安装 Go，执行 `frontend-go/backend-go` 的 Go 测试；构建 Go 栈 Docker 镜像。

两个栈可以同时跑，但失败只表示对应栈有问题，不再表示另一套后端必须同步修复。

## 文档更新

需要更新：

- `AGENTS.md`：删除“两套后端必须暴露相同能力”的默认要求，改为两个项目可独立演进。
- `README.md`：说明两个项目的启动、测试和目录边界。
- `docs/README.md`、`docs/architecture.md`、`docs/api-contract.md`、`docs/operability.md`：将“共享契约是事实来源”改为“每个栈内部契约是事实来源”。

学习和面试材料可以保留 Java 项目为主的表达，但要避免说 Go 后端必须同步实现同一业务能力。

## 验证策略

实施完成后至少运行：

```powershell
npm run test:java-stack
npm run test:go-stack
npm run build:java-stack
npm run build:go-stack
```

如果 Docker 可用，再运行：

```powershell
docker compose -f frontend-java/docker-compose.yml build
docker compose -f frontend-go/docker-compose.yml build
```

## 非目标

- 不拆成两个 Git 仓库。
- 不引入共享前端包。
- 不继续强制 Java 和 Go 后端契约一致。
- 不在本次重构中改业务逻辑。
- 不把 `node_modules`、构建产物或缓存复制进新项目。

## 风险与取舍

物理复制会带来前端代码重复。这个成本是有意接受的，因为当前目标是允许任意一种后端独立修改，不再被另一套实现约束。

迁移会影响大量路径引用，包括 CI、Docker、文档、根脚本和 README。实施时需要用搜索确认没有旧 `apps/` 或 `packages/shared-contract` 路径残留在可执行配置中。

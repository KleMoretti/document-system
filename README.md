# 在线文档协同编辑系统

这个仓库是一个 monorepo：

- `apps/web`: React + TypeScript + Vite 前端
- `apps/backend-java`: Java 21 + Spring Boot 后端
- `apps/backend-go`: Go 后端
- `packages/shared-contract`: REST、WebSocket、SQL schema 统一契约
- `infra`: Redis Docker fallback

## 本机版本基线

优先使用本机环境：

- Java: 21.0.4 LTS
- Maven: `D:\apache-maven-3.8.9`
- Go: 1.24.13
- Node.js: 20.20.2
- npm: 10.8.2
- MySQL: 8.4.7
- Redis: `D:\Redis`，默认 `127.0.0.1:6379`

## Redis

优先启动本机 Redis：

```powershell
D:\Redis\start.bat
```

或手动启动：

```powershell
Set-Location D:\Redis
.\redis-server.exe redis.conf
```

验证：

```powershell
& 'D:\Redis\redis-cli.exe' ping
```

如果本机 Redis 不可用，使用 Docker fallback：

```powershell
docker compose -f infra/docker-compose.yml up redis
```

## Docker 一键启动

默认 Compose 启动 Web、Java 后端、MySQL 和 Redis：

```powershell
docker compose up --build
```

启动后访问：

- Web: `http://localhost:15173`
- Java API: `http://localhost:18080`
- MySQL: `127.0.0.1:3307`
- Redis: `127.0.0.1:6380`

Compose 默认把 Web、Java API、MySQL、Redis 映射到 `15173/18080/3307/6380`，避免和本机 `5173/8080/3306/6379` 服务冲突。容器网络内部仍使用 `web:80`、`backend-java:8080`、`mysql:3306` 和 `redis:6379`。

如果要用 Go 后端验证同一套契约，启动 Go profile：

```powershell
docker compose --profile go up --build backend-go web-go mysql redis
```

启动后访问：

- Web-Go: `http://localhost:15174`
- Go API: `http://localhost:18081`

如果要验证 Java 微服务拆分拓扑，启动 split profile：

```powershell
docker compose --profile split up --build backend-java-auth backend-java-document backend-java-realtime web-split mysql redis
```

启动后访问：

- Web-Split: `http://localhost:15175`
- Auth API: `http://localhost:18082`
- Document API: `http://localhost:18083`
- Realtime WebSocket: `ws://localhost:18084`

split profile 默认让前端登录和 `/api/me` 请求访问 auth 服务，文档 REST 请求访问 document 服务，WebSocket 连接访问 realtime 服务。`SERVICE_TOKEN` 必须在 auth 和 document 服务之间保持一致；`SPLIT_ALLOWED_ORIGINS` 默认允许 `15175` 前端来源。

常用清理命令：

```powershell
docker compose down
docker compose down -v
```

`docker compose down -v` 会删除 MySQL / Redis 数据卷。需要修改端口、数据库密码、JWT secret 或前端连接地址时，复制 `.env.example` 为 `.env` 后调整其中的 Docker Compose 变量。

## MySQL

本机 root 密码按当前环境使用 `123456`。

创建数据库：

```powershell
mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS documentation_collab CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

执行 schema：

```powershell
mysql -u root -p123456 documentation_collab -e "source D:/@Java/documentation/packages/shared-contract/sql/schema.mysql.sql"
```

## 启动 Java 后端

```powershell
Set-Location D:\@Java\documentation\apps\backend-java
$env:MYSQL_USER='root'
$env:MYSQL_PASSWORD='123456'
$env:MYSQL_DATABASE='documentation_collab'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
$env:JWT_SECRET='local-documentation-secret-please-change'
$env:ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173'
D:\apache-maven-3.8.9\bin\mvn.cmd spring-boot:run
```

## 启动 Go 后端

```powershell
Set-Location D:\@Java\documentation\apps\backend-go
$env:MYSQL_USER='root'
$env:MYSQL_PASSWORD='123456'
$env:MYSQL_DATABASE='documentation_collab'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
$env:JWT_SECRET='local-documentation-secret-please-change'
$env:ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173'
go run .
```

`JWT_SECRET` 必须显式设置，不能使用默认开发密钥；`ALLOWED_ORIGINS` 控制 REST CORS 和 WebSocket Origin 白名单。

## 启动前端

连接 Java 后端：

```powershell
Set-Location D:\@Java\documentation
npm install
npm --prefix apps/web run dev -- --mode java
```

连接 Go 后端：

```powershell
Set-Location D:\@Java\documentation
npm install
npm --prefix apps/web run dev -- --mode go
```

## 验证命令

```powershell
npm run test:web
npm run build:web
go test ./...
D:\apache-maven-3.8.9\bin\mvn.cmd test
```

## 可观测性与压测

Java 和 Go 后端都提供 Prometheus 文本格式指标：

```powershell
Invoke-WebRequest http://localhost:18080/metrics -UseBasicParsing
Invoke-WebRequest http://localhost:18081/metrics -UseBasicParsing
```

WebSocket 压测工具：

```powershell
npm run loadtest:ws -- -url ws://localhost:18080/ws/documents -doc-id <uuid> -token <jwt> -clients 100 -duration 30s -interval 1s -mode presence
```

更多说明见 `docs/operability.md`。

安全相关默认值：

- `JWT_TTL=2h`
- `BCRYPT_COST=12`
- WebSocket JWT 通过子协议传递，新客户端不再把 token 放入 URL 查询参数。

更多安全说明见 `docs/security-notes.md`。

## 端口

- Web: `http://localhost:5173`
- Java API: `http://localhost:8080`
- Go API: `http://localhost:8081`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`

# 在线文档协同编辑系统

这个仓库是一套 monorepo：

- `apps/web`: React + TypeScript + Vite 前端
- `apps/backend-java`: Java 21 + Spring Boot 后端
- `apps/backend-go`: Go 后端
- `packages/shared-contract`: REST、WebSocket、SQL schema 统一契约
- `infra`: Redis Docker fallback

## 本机版本基线

优先使用本机环境：

- Java: 21.0.4 LTS
- Go: 1.24.13
- Node.js: 20.20.2
- npm: 10.8.2
- MySQL: 8.4.7
- Redis: `D:\Redis`, 默认 `127.0.0.1:6379`

## Redis

优先启动本机 Redis：

```powershell
D:\Redis\start.bat
```

或使用同样的相对配置路径手动启动：

```powershell
cd /d D:\Redis
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

## MySQL

创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS documentation_collab
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

执行 schema：

```powershell
mysql -u root -p documentation_collab < packages/shared-contract/sql/schema.mysql.sql
```

## 启动

前端连接 Java 后端：

```powershell
copy apps\web\.env.java apps\web\.env.local
npm install
npm run dev:web
npm run dev:java
```

前端连接 Go 后端：

```powershell
copy apps\web\.env.go apps\web\.env.local
npm run dev:web
npm run dev:go
```

## 端口

- Web: `http://localhost:5173`
- Java API: `http://localhost:8080`
- Go API: `http://localhost:8081`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`

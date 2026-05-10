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
go run .
```

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

## 端口

- Web: `http://localhost:5173`
- Java API: `http://localhost:8080`
- Go API: `http://localhost:8081`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`

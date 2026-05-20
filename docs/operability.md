# Operability

This project includes a minimal production-readiness layer for CI, observability, and WebSocket load testing.

## CI

GitHub Actions workflow: `.github/workflows/ci.yml`

The workflow runs:

- Web unit tests and production build.
- Go tests, including the WebSocket load-test utility package.
- Java Maven tests.
- Docker builds for Java backend, Go backend, Java-connected web image, and Go-connected web image.

## Metrics

Both backends expose Prometheus-compatible metrics at:

```text
GET /metrics
```

Metrics currently emitted:

- `documentation_collab_http_requests_total{method,path,status}`
- `documentation_collab_ws_connections_total`
- `documentation_collab_ws_connections_active`
- `documentation_collab_ws_messages_total{type}`

Example local checks:

```powershell
Invoke-WebRequest http://localhost:18080/metrics -UseBasicParsing
Invoke-WebRequest http://localhost:18081/metrics -UseBasicParsing
```

## WebSocket Load Test

The load-test utility lives in the Go backend module so it can reuse the existing WebSocket dependency.

```powershell
npm run loadtest:ws -- -url ws://localhost:18080/ws/documents -doc-id <uuid> -token <jwt> -clients 100 -duration 30s -interval 1s -mode presence
```

For write-path testing, use `-mode update` with an editor or owner token:

```powershell
npm run loadtest:ws -- -url ws://localhost:18080/ws/documents -doc-id <uuid> -token <jwt> -clients 100 -duration 30s -interval 1s -mode update
```

The command sends the JWT through the WebSocket `Sec-WebSocket-Protocol` header (`bearer, <jwt>`) instead of the URL query string. It prints JSON with connected clients, sent messages, errors, and write-call latency percentiles.

## Report Template

Record load-test runs with this format:

```text
Date:
Backend: Java | Go
Commit:
Environment:
Clients:
Duration:
Mode: presence | update
Connected:
Messages sent:
Errors:
P50:
P95:
Max:
Notes:
```

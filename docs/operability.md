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

## Health Checks

Both backends expose:

```text
GET /healthz
GET /readyz
```

`/healthz` verifies process liveness. `/readyz` verifies required dependencies, currently MySQL, and returns HTTP 503 when the service should not receive traffic.

Metrics currently emitted:

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

The command sends the JWT through the WebSocket `Sec-WebSocket-Protocol` header (`bearer, <jwt>`) instead of the URL query string. It prints JSON with connection counts, message counts, error counts, error stages, server error codes, write-call latency percentiles, and receive latency percentiles when echoed update or presence payloads include load-test timestamps.

Important output fields:

- `clients`: requested client count.
- `connected`: clients that completed the WebSocket handshake and received `sync:init`.
- `sent`: messages successfully written by the load-test clients.
- `received`: messages read by all load-test clients.
- `errors`: client-side dial, init, read, or write errors.
- `disconnects`: read-side disconnects before the test deadline.
- `error_codes`: server `error.code` counts observed by clients, such as `SLOW_CLIENT`.
- `error_stages`: client-side failure stages, currently `dial`, `init`, `read`, and `write`.
- `latency_*`: local WebSocket write-call latency percentiles, not durable persistence latency.
- `receive_latency_*`: end-to-end receive latency inferred from timestamps embedded in echoed load-test messages.

Use `presence` mode to stress broadcast fan-out without MySQL writes. Use `update` mode to stress the full edit path: WebSocket receive, update validation, per-document batch persistence, Redis/local broadcast, and outbound queue delivery. Presence tests are intentionally harsher than realistic cursor traffic when every client sends every second, because each message fans out to the same document's active connections.

Before each comparable run, restart the target backend or record the pre-run `/metrics` values so counters can be interpreted correctly. For Docker-local Java tests, a typical setup is:

```powershell
docker compose up -d mysql redis
docker run -d --name documentation-collab-backend-java --network documentation_default -p 18080:8080 `
  -e MYSQL_HOST=mysql -e MYSQL_PASSWORD=root `
  -e REDIS_HOST=redis `
  -e JWT_SECRET=local-documentation-secret-please-change `
  -e WS_SEND_QUEUE_SIZE=128 `
  documentation-backend-java:loadtest
```

The `documentation-backend-java:loadtest` image used in local validation is a temporary image built from the current Spring Boot jar. It is not part of the committed Docker Compose contract.

## High-Concurrency Tuning

Both backends support the same environment variables for hotspot collaborative editing:

```text
DB_MAX_OPEN_CONNS=50
DB_MAX_IDLE_CONNS=25
WS_SEND_QUEUE_SIZE=32
WS_BATCH_MAX_SIZE=32
WS_BATCH_FLUSH_MS=25
WS_SNAPSHOT_MIN_UPDATES=100
```

`WS_SEND_QUEUE_SIZE` bounds each WebSocket connection's outbound queue. When a client cannot keep up and its queue fills, the backend sends `SLOW_CLIENT` and closes that connection so one slow browser cannot block the document broadcast path.

`WS_BATCH_MAX_SIZE` and `WS_BATCH_FLUSH_MS` control short-cycle persistence batching for `sync:update`. The backend still broadcasts only after MySQL persistence succeeds, but concurrent updates for the same document can share one transaction and one contiguous sequence allocation.

`WS_SNAPSHOT_MIN_UPDATES` prevents many editors from compacting the same document too often. A snapshot is accepted only when its `snapshotSeq` matches the latest persisted state and it covers at least this many incremental updates.

## Load-Test Design and Interpretation

Run at least these scenarios when changing the collaborative editing path:

- `presence` smoke: 100 clients, 20-30 seconds, 1 second interval.
- `update` smoke: 100 clients, 10-30 seconds, 1 second interval.
- `update` capacity check: increase clients until connected rate, `errors`, `disconnects`, receive P95, `SLOW_CLIENT`, and backend queue depth show the local capacity boundary.
- `presence` capacity check: run separately from update results because it measures pure fan-out pressure and can fail earlier.

For a healthy run on a local single-backend setup:

- `connected` should equal `clients`.
- `errors` and `disconnects` should be zero for smoke tests.
- `documentation_collab_ws_connections_active` should return to zero after the command exits.
- `documentation_collab_ws_slow_clients_total` should remain zero for the target scenario.
- `documentation_collab_ws_send_queue_depth_max` should stay below `WS_SEND_QUEUE_SIZE`; hitting the queue size means at least one client could not keep up.
- In `update` mode, `documentation_collab_ws_batch_size_sum` should match the number of persisted updates, while `_count` shows how many MySQL batch flushes were needed.

Recent local Docker validation on the Java backend used MySQL 8.4, Redis 8, `documentation-backend-java:loadtest`, and `WS_SEND_QUEUE_SIZE=128`:

```text
Mode: update
Clients: 300
Duration: 20s
Connected: 300/300
Sent: 5600
Received: 1680000
Errors: 0
Disconnects: 0
Receive P95: 188.9312ms
Backend batches: 5600 updates in 232 flushes
Slow clients: 0
Max queue depth: 38
```

```text
Mode: presence
Clients: 100
Duration: 20s
Connected: 100/100
Sent: 1900
Received: 190000
Errors: 0
Disconnects: 0
Receive P95: 33.6942ms
Slow clients: 0
Max queue depth: 92
```

The same local Docker environment did not produce a healthy 200-500 client `presence` run, even with `WS_SEND_QUEUE_SIZE=128`. Those runs filled the outbound queue and produced `SLOW_CLIENT` disconnects. Treat that as the current single-node fan-out boundary for the synthetic "every client sends presence every second" pattern, not as the capacity limit for realistic editing where fewer clients emit document updates.

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
Messages received:
Errors:
Disconnects:
Error codes:
Error stages:
P50:
P95:
Max:
Backend ws active after run:
Backend slow clients:
Backend max queue depth:
Backend batch count:
Backend batch max:
Notes:
```

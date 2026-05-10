# Architecture

The frontend is backend-agnostic. It talks to either backend through the same REST API and WebSocket message contract.

```mermaid
flowchart LR
  Web[React + Tiptap + Yjs] -->|REST| Java[Java Spring Boot]
  Web -->|WebSocket| Java
  Web -->|REST| Go[Go Backend]
  Web -->|WebSocket| Go
  Java --> MySQL[(MySQL)]
  Go --> MySQL
  Java --> Redis[(Redis Pub/Sub)]
  Go --> Redis
```

Yjs is the source of truth for collaborative rich-text state. Backends authenticate users, authorize documents, persist Yjs updates in MySQL, and fan out updates through Redis.

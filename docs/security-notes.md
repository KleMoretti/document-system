# Security Notes

This project is still a portfolio-grade collaborative editor, not a hardened production deployment. The following defaults are now explicit so they can be reviewed and tuned per environment.

## Authentication

- REST APIs use `Authorization: Bearer <jwt>`. Because credentials are not sent automatically by the browser, CSRF tokens are not required for this auth mode.
- XSS remains the primary risk for browser-held JWTs. The frontend sanitizes imported HTML before it enters the editor, and production deployments should add a strict Content Security Policy at the reverse proxy layer.
- JWT lifetime is controlled by `JWT_TTL` and defaults to `2h`.
- Password hashing cost is controlled by `BCRYPT_COST` and defaults to `12`.

## WebSocket Tokens

New clients send JWTs through the WebSocket subprotocol list:

```ts
new WebSocket("/ws/documents/{docId}", ["bearer", token])
```

The legacy `?token=` query parameter remains accepted for compatibility, but should not be used by new clients because URLs are more likely to appear in browser history and proxy logs.

## Redis

Redis TLS can be enabled with `REDIS_TLS=true` when the deployment provides a TLS-enabled Redis endpoint. Local Docker Compose keeps Redis plaintext on the private Compose network for developer convenience.

# Architecture

## Request path

```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant Router
    participant Primary
    participant Fallback

    Client->>Gateway: POST /v1/chat/completions
    Gateway->>Gateway: request ID, API key auth, rate limit
    Gateway->>Router: resolve logical model
    Router-->>Gateway: ordered providers
    Gateway->>Primary: mapped provider model
    alt primary succeeds or returns 4xx
        Primary-->>Gateway: streaming or JSON response
    else primary returns 5xx or connection error
        Gateway->>Fallback: mapped provider model
        Fallback-->>Gateway: streaming or JSON response
    end
    Gateway-->>Client: upstream-compatible response
```

## Design decisions

### Logical models

Clients request a stable logical model such as `smart-chat`. Routing configuration maps that model to
an ordered provider list and maps it again to each provider's physical model. Provider changes therefore
do not require client releases.

### Reactive proxy

Spring WebFlux keeps streaming responses non-blocking and avoids buffering complete model responses in
gateway memory. This matters for server-sent event token streams and slow upstream generations.

### Failure policy

Connection failures, timeouts, and upstream 5xx responses trigger the next configured provider. Upstream
4xx responses are forwarded because retrying invalid input or exhausted provider quota against every
provider can increase cost and hide the real error.

### Current limits

The first milestone uses an in-memory fixed-window limiter. It is intentionally isolated behind a filter
so a Redis-backed distributed limiter can replace it when multiple gateway instances are introduced.

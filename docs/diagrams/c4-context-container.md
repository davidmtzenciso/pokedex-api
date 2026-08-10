# C4 — Context and Containers

What runs, and who talks to what. This is the picture to draw on a whiteboard first.

```mermaid
flowchart TB
    subgraph external["External actors and systems"]
        CLIENT["Any API client<br/>independently released"]
        POKEAPI["PokeAPI v2<br/>pokeapi.co<br/>unauthenticated, rate-limited"]
    end

    subgraph runtime["pokedex-api runtime (docker compose)"]
        API["pokedex-api<br/>Spring Boot 4 monolith<br/>:8080, context-path /api"]
        DB[("PostgreSQL 17<br/>pokedex<br/>:5432")]
        CACHE[("Redis 7<br/>upstream cache + jti sessions<br/>:6379")]
    end

    CONTRACT["pokedex-api.yaml<br/>committed; a git tag is a version"]

    CLIENT -->|"REST + Bearer JWT"| API
    API -->|"JDBC + Flyway"| DB
    API -->|"RESP: cache reads, jti sessions"| CACHE
    API -->|"HTTPS RestClient<br/>virtual-thread fan-out"| POKEAPI
    API -.->|"serves /v3/api-docs"| CONTRACT
    CONTRACT -.->|"pinned at build time"| CLIENT
```

## What it encodes

- **Three containers, no more.** No queue, no service mesh, no second service. The problem does not justify them.
- **Clients sit outside the boundary.** They are independently deployed and versioned, and the only thing they depend on is the published contract — see [ADR-0009](../adr/0009-no-bundled-client.md).
- **Two different relationships with Redis.** It is both an upstream cache and the `jti` session store. Cache reads fail *open*; session reads fail *closed*. That asymmetry is deliberate and is the single easiest thing to get wrong.
- **PokeAPI is the only outbound dependency** and it is public, unauthenticated, and rate-limited — which is why the edge into it is drawn as a bounded fan-out rather than a plain call.

## Related

[Package dependencies](package-dependencies.md) · [Deployment](deployment.md) · [Contract distribution](contract-distribution.md)

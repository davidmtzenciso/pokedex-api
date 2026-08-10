# ADR-0006: Redis Cache and Bounded Virtual-Thread Fan-Out for PokeAPI

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

User Story 01 requires a paginated list showing each Pokémon's sprite, category, mass, and abilities. PokeAPI cannot serve this in one call:

- `GET /api/v2/pokemon?limit=20&offset=0` returns **only** `{name, url}` (verified 2026-08-09)
- Sprite, weight, abilities, stats, and types require `GET /pokemon/{id}` per row
- Category (`genera[]` filtered to English) requires `GET /pokemon-species/{id}` per row

A page of N rows therefore costs **1 + 2N upstream HTTP requests**:

| Page size | Upstream calls | Sequential at ~80 ms |
|---:|---:|---|
| 10 (default) | **21** | ~1.7 s |
| 20 | 41 | ~3.3 s |
| 100 (maximum) | **201** | ~16 s |

The exercise lists caching as a "nice to have." At 21 calls for a *default* page — and 201 for the largest one a client may request — it is not optional; it is what makes the feature viable. The page-size cap of 100 exists for the same reason: it bounds the worst case a single request can inflict on a free, fair-use-limited public API.

## Decision

Three mechanisms, layered:

1. **Redis cache** keyed by upstream resource (`pokeapi:pokemon:{id}`, `pokeapi:species:{id}`, `pokeapi:page:{offset}:{limit}`) with a 24-hour TTL. Pokémon reference data is effectively static, so a long TTL is safe. Explicit eviction on re-sync keeps the local replica and the cache consistent.
2. **Bounded virtual-thread fan-out** in `PokeApiCatalogAdapter`. The 40 detail and species calls run concurrently on virtual threads via `CompletableFuture`, gated by a `Semaphore(16)`. Virtual threads make 40 blocking calls cheap without a reactive rewrite; the semaphore stops us from issuing 3,000 concurrent requests during a batch sync (IA10).
3. **Local replication as the real answer.** Once a Pokémon is synced (US03), reads never touch PokeAPI at all. The cache covers browsing; the replica covers everything the curator has claimed.

Resilience on the adapter: 2 s connect / 5 s read timeouts, 3 retries with exponential backoff on 5xx and timeouts, **no retry on 4xx**, and a circuit breaker after 5 consecutive failures. When the breaker is open, reads fall back to the local replica and flag `stale: true`; with no replica, 502.

Cache reads **fail open** — a Redis outage logs a warning and falls through to upstream. This is the deliberate opposite of the session store's fail-closed behaviour ([ADR-0005](0005-es256-jwt-jti-sessions.md)): a cache is an optimisation, a session store is a security control.

## Alternatives Considered

1. **Caffeine in-process cache** — No extra container, simpler compose file, and entirely adequate for a single instance. Rejected because it dies with the process, so every restart during a demo replays the full cold path, and because Redis is already required for `jti` sessions — the marginal infrastructure cost is zero.
2. **No cache; rely solely on local replication** — Defensible: sync everything up front and serve locally. Rejected because US01 explicitly describes *browsing* the catalogue, which must work for Pokémon that have never been synced.
3. **Reactive WebFlux for the fan-out** — The conventional answer to high-fan-out I/O. Rejected because the persistence layer is blocking JPA, so a reactive web layer would bridge back to a bounded pool anyway, and because WebFlux is rejected platform-wide. Virtual threads deliver the same concurrency with an imperative, debuggable stack trace.
4. **Sequential fetching with a longer TTL** — Simplest code. Rejected: a three-second cold page is a bad first impression in a live demo, and R1 in the risk register already treats cold-path latency as the top risk.

## Consequences

### Positive
- Cold page ≈ 1 + 2N upstream calls (21 at the default size); warm page ≈ 1 Redis read.
- The cache survives application restarts, so a demo stays fast after a rebuild.
- Bounded concurrency makes batch sync well-behaved against a public API.
- The `1 + 2N` observation is the single strongest architectural talking point in the presentation, and the design visibly answers it — with a cache, a bounded fan-out, and a page-size cap that bounds the worst case.

### Negative
- One more container, one more failure mode, and cache invalidation to reason about on re-sync.
- A 24-hour TTL means a genuine upstream correction can take a day to appear. Acceptable for Pokédex data; explicitly wrong for anything transactional.
- Cache-key design becomes part of the API contract in practice — changing pagination semantics invalidates keys.

### Neutral
- Requires `spring-boot-starter-data-redis`, already present for sessions.
- Component tests need a Redis Testcontainer; unit tests use an in-memory `CachePort` fake.
- Startup cache warming for the seeded first 151 is a demo affordance, not a correctness requirement.

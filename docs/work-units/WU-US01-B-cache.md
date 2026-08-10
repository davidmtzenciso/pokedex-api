# WU-US01-B — Redis Cache

| Field | Value |
|---|---|
| **Work Unit** | WU-US01-B |
| **Parent** | [WF-US01 Pokémon Enumeration](../workflows/WF-US01-pokemon-enumeration.md) |
| **Objective contribution** | Turns a 21-call cold page into a one-read warm page |
| **Estimate** | S |
| **Status** | done |

## Objective

Implement `CachePort` over Redis, with a policy that fails **open**.

## Entry Criteria

- WU-000-C green (`CachePort` declared)

## Outputs

- `RedisCacheAdapter` with eviction and a fail-open component test

---

## ▶ Activity Sequence

### B1 — `RedisCacheAdapter`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `infrastructure/…/cache/RedisCacheAdapter.java` + test |
| **Intent** | A cache is an optimisation; its outage must not become an outage |
| **Depends on** | — (entry) |

**How**
Keys `pokeapi:pokemon:{id}`, `pokeapi:species:{id}`, `pokeapi:page:{offset}:{limit}`.
TTL 24 h — Pokémon reference data is effectively static. On
`RedisConnectionFailureException`: log WARN, return empty, fall through to upstream.

**Conventions**
- Cache reads fail **open** → [error handling](../handbook/error-handling.md)

**Avoid**
- Letting a Redis outage surface as a 500. That is a cache doing the opposite of its job

| Field | Value |
|---|---|
| **Produces** | Cache adapter |
| **Verify** | Component test with Redis stopped mid-run |
| **Pass when** | Requests still succeed from upstream, with a WARN logged |
| **On fail / Rollback** | — |

### B2 — Eviction on re-sync

| Field | Value |
|---|---|
| **Type** | edit |
| **Target** | `RedisCacheAdapter`, wired from the sync use cases |
| **Intent** | A freshly synced record must not be shadowed by a stale cached page |
| **Depends on** | B1 |

**How**
Evict `pokeapi:pokemon:{id}` and every `pokeapi:page:*` on a successful sync.

**Avoid**
- Relying on TTL alone. A 24-hour window in which the cache disagrees with the database is a bug, not a delay

| Field | Value |
|---|---|
| **Produces** | Coherent cache |
| **Verify** | Component test: sync, then read, and assert fresh data |
| **Pass when** | The post-sync read reflects the new state immediately |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [x] Cache outage degrades rather than fails — proven against a Redis container stopped mid-test; reads, writes and evictions all continue
- [x] A warm page issues **zero** upstream calls (AC-US01-4) — 21 cold, 0 warm
- [x] Re-sync evicts — `evictByPrefix` added to `CachePort`, and the page keys go back upstream after eviction

> **The command timeout is part of failing open.** With Lettuce's default, a read against a
> stopped Redis blocked for ~5 minutes before surfacing an error — the cache stalls the
> request instead of degrading it. `spring.data.redis.timeout` bounds it in production and
> the component test bounds it the same way.

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US01-4 |
| Decision | [ADR-0006](../adr/0006-redis-cache-pokeapi-fanout.md) |

## Blocks

WU-US01-C

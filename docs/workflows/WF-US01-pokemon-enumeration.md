# WF-US01 — Pokémon Enumeration

> **User story**: *The system should allow users to browse Pokémon via paginated results, displaying each entry's sprite, category, mass, and a collection of their skills.*
> **Nice to have (in scope)**: caching for service responses.
> **Depends on**: [WF-000](WF-000-foundation.md)
> **Delivers**: `GET /v1/pokedex/pokemon` — paginated, cached, complete rows
> **Estimate**: L
> **Work units**: [WU-US01-A](../work-units/WU-US01-A-catalog-adapter.md) · [WU-US01-B](../work-units/WU-US01-B-cache.md) · [WU-US01-C](../work-units/WU-US01-C-list-endpoint.md)

> Shared specification — IAR, domain model, hard rules, ArchUnit — lives in
> [WF-000](WF-000-foundation.md) and is not restated here.

---

## 1. Summary

This is the story the whole backend design exists to serve, because the upstream API cannot
serve it. `GET /api/v2/pokemon` returns **only** `{name, url}` — no sprite, no mass, no
abilities — and the category lives on a third resource, `/pokemon-species/{id}`.

A page of N rows therefore costs **1 + 2N** upstream calls: 21 at the default page size of
10, and 201 at the maximum of 100. Everything else in this workflow follows from that one
fact.

---

## 2. Design decisions specific to this story

| # | Decision | Alternatives | Rationale | Consequence |
|---|---|---|---|---|
| 1 | Redis cache keyed on upstream resource, 24 h TTL | Caffeine; no cache | Survives restarts; TTL matches effectively-static data | Cold page `1 + 2N`, warm page 1 read — [ADR-0006](../adr/0006-redis-cache-pokeapi-fanout.md) |
| 2 | Bounded virtual-thread fan-out, `Semaphore(16)` | Reactive; sequential; `parallelStream` | 2N blocking calls are cheap on virtual threads and the stack stays debuggable | Concurrency bounded on two axes |
| 3 | Page size default 10, maximum 100, **reject above** | Spring's silent clamp | A clamped response lets a caller believe it received everything | 400 `INVALID_PAGINATION` naming the cap |
| 4 | Degrade to the local replica when upstream is down | 502 always | A stale answer beats no answer when we have one | 200 with `stale: true` |

Decision tree: [read-path-decision-tree.md](../diagrams/read-path-decision-tree.md).

---

## 3. Specification

| Verb | Path | Auth | Success | Errors |
|---|---|:---:|---|---|
| GET | `/v1/pokedex/pokemon?page&size` | 🔓 public | 200 `PokemonPageDTO` | 400, 502, 504 |

`page` defaults to `0`. `size` defaults to **10**, minimum 1, maximum **100**.

Each row carries — this is the story's actual requirement:

| Field | Source | Note |
|---|---|---|
| `spriteUrl` | `pokemon.sprites.other['official-artwork'].front_default` | falls back to `front_default` |
| `category` | `species.genera[]` filtered to `language.name == "en"` → `.genus` | **third resource** (IA2) |
| `massKilograms` | `pokemon.weight` ÷ 10 | upstream is hectograms (IA3) |
| `abilities[]` | `pokemon.abilities[].ability.name` | the story's "skills" |
| `stale` | derived | `true` when served from the local replica during an upstream outage |

---

## 4. Domain delta

No new aggregates. This story reads through `PokemonCatalog` and `CachePort`, both declared
in WF-000. It adds `PokemonSummary` as an application-layer result record.

Relevant derived values ([WF-000 §4.7](WF-000-foundation.md)): `massKilograms`,
`page.totalPages`, `page.upstreamCallCount ≡ 1 + 2·size`.

---

## 5. Flow

[sequence-list-page.md](../diagrams/sequence-list-page.md) — the cached path, the cold
fan-out, and both upstream-failure branches.

---

## 9.5 Error paths

| Condition | Response | Note |
|---|---|---|
| `size > 100` / `size < 1` / `page < 0` | 400 `INVALID_PAGINATION` | Reject, never clamp |
| Upstream 5xx after 3 retries, **no** local replica | 502 `UPSTREAM_UNAVAILABLE` | Never a 500 — it is not our outage |
| Upstream 5xx after 3 retries, local replica **exists** | 200 with `stale: true` | Subtle badge client-side |
| Read timeout > 5 s | 504 `UPSTREAM_TIMEOUT` | |
| Circuit breaker open | 503 + `Retry-After` | |
| Redis unreachable on a cache read | Log WARN, **fail open**, go upstream | A cache outage is not an outage |

---

## 10. Acceptance criteria

**AC-US01-1**: Given a seeded database, when `GET /api/v1/pokedex/pokemon` is called with no
parameters, then **10** rows return, **each** carrying a non-null sprite URL, category, mass
in kilograms, and a non-empty abilities array.

**AC-US01-2**: Given `size=101`, then 400 `INVALID_PAGINATION` returns naming the maximum of
100. Given `size=100`, the request succeeds.

**AC-US01-3**: Given a cold cache, when a default page is requested, then exactly **21**
upstream calls are issued and never more than 16 concurrently.

**AC-US01-4**: Given a warm cache, when the same page is requested, then **zero** upstream
calls are issued.

**AC-US01-5**: Given PokeAPI unreachable and no local replica, then 502
`UPSTREAM_UNAVAILABLE` returns as `application/problem+json`.

**AC-US01-6**: Given PokeAPI unreachable and a local replica present, then 200 returns with
`stale: true`.

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R1 | Cold fan-out makes the first page slow in the live demo | High | High | R | Default size 10 keeps it at 21 calls; seed the first 151; warm the cache on startup |
| R2 | PokeAPI rate-limits during the demo | Med | High | R | `Semaphore(16)`, backoff, and the page-size cap |
| R7 | Category comes back Japanese | Med | Med | Y | Filter `genera[]` to `en` explicitly; never take `[0]` |

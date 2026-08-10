# WU-US01-A — PokeAPI Catalog Adapter

| Field | Value |
|---|---|
| **Work Unit** | WU-US01-A |
| **Parent** | [WF-US01 Pokémon Enumeration](../workflows/WF-US01-pokemon-enumeration.md) |
| **Objective contribution** | The upstream integration, and every way it can fail |
| **Estimate** | L |
| **Status** | not started |

## Objective

Implement `PokemonCatalog` against PokeAPI: bounded concurrent fan-out, correct mapping of
a hostile payload shape, and a resilience story for every failure mode.

## Entry Criteria

- WU-000-C green (ports declared)
- IAR probes IA1–IA6 re-confirmed against the live API

## Inputs

| Input | Source | Used by |
|---|---|---|
| Upstream quirks IA1–IA6 | [WF-000 §3.0](../workflows/WF-000-foundation.md) | F2 |
| Fan-out design | [concurrency](../handbook/concurrency.md) | F3 |
| Resilience policy | [concurrency](../handbook/concurrency.md) — timeouts, retry and the circuit breaker are specified there, not in WF-000 | F1 |

## Outputs

- `PokeApiCatalogAdapter`, `PokeApiMapper`, WireMock contract tests for every failure mode

---

## ▶ Activity Sequence

### F1 — `RestClient` with timeouts and retry policy

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `infrastructure/…/pokeapi/PokeApiClient.java`, `PokeApiProperties.java` |
| **Intent** | A call with no timeout is a resource leak with a delay fuse |
| **Depends on** | — (entry) |

**How**
`RestClient` — never `RestTemplate`, never `WebClient`. 2 s connect, 5 s read. 3 retries
with exponential backoff on 5xx and timeouts, **no retry on 4xx**. Circuit breaker after 5
consecutive failures. Config as a `@ConfigurationProperties` record.

**Conventions**
- `RestClient` only; `@ConfigurationProperties` records → [spring patterns](../handbook/spring-patterns.md)

**Avoid**
- Retrying a 404. It will never become a 200, and three retries turn one wasted call into four

| Field | Value |
|---|---|
| **Produces** | Configured client |
| **Verify** | WireMock test with a fixed delay beyond the read timeout |
| **Pass when** | `UpstreamTimeoutException` raised; no retry observed on a stubbed 404 |
| **On fail / Rollback** | — |

### F2 — `PokeApiMapper` — absorb every upstream quirk here

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `…/pokeapi/PokeApiMapper.java` + tests |
| **Intent** | One place that knows PokeAPI is awkward, so nothing downstream has to |
| **Depends on** | F1 |

**How**
Five transformations, each a documented IAR entry:

| Quirk | Handling |
|---|---|
| `weight` hectograms, `height` decimetres (IA3) | Construct `Mass`/`Height`; convert only in the VO |
| `flavor_text` contains `\n` and `\f` (IA4) | `Description` normalises on construction |
| `genera[]` and `flavor_text_entries[]` are multilingual (IA2) | Filter to `language.name == "en"`; **never** take `[0]` |
| `evolves_to[]` is a recursive tree (IA5) | Flatten to an edge list, recursively |
| `species.names[]` has 12 locales (IA6) | Seed `localizedNames` with `source = UPSTREAM` |

**Patterns**
- Adapter-side mapper; the domain never sees a `PokeApiPokemonResponse` → [design patterns](../handbook/design-patterns.md)
- Recursive flatten, `mapMulti` at each level → [stream API](../handbook/stream-api.md)

**Avoid**
- Assuming a linear evolution chain. **Eevee has eight branches** and a flat mapper truncates it silently

| Field | Value |
|---|---|
| **Produces** | The mapper |
| **Verify** | `mvn -B test -Dtest=PokeApiMapperTest,EvolutionChainMapperTest` |
| **Pass when** | Eevee (chain 67) yields **8** branches; Bulbasaur maps to 6.9 kg; no control characters survive |
| **On fail / Rollback** | — |

### F3 — Bounded virtual-thread fan-out

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `…/pokeapi/PokeApiCatalogAdapter.java` + test |
| **Intent** | `1 + 2N` calls per page, concurrent but bounded on both axes |
| **Depends on** | F1, F2 |

**How**
`Executors.newVirtualThreadPerTaskExecutor()` in try-with-resources, `CompletableFuture`
per row, gated by `Semaphore(16)`. The page-size cap of 100 bounds how many a single
request can queue; the semaphore bounds how many are in flight.

**Conventions**
- Virtual threads, not reactive; not `parallelStream()` → [concurrency](../handbook/concurrency.md)

**Avoid**
- Structured concurrency — preview at language level 24, therefore banned → [ADR-0004](../adr/0004-java-24-language-level.md)
- Holding a lock across a blocking call on a virtual thread (`java:S6906`)

| Field | Value |
|---|---|
| **Produces** | The catalog adapter |
| **Verify** | WireMock test counting concurrent in-flight requests with a `CountDownLatch` |
| **Pass when** | Never more than 16 concurrent; a default page issues 21 calls |
| **On fail / Rollback** | — |

### F4 — Failure-mode contract tests

| Field | Value |
|---|---|
| **Type** | test |
| **Target** | `…/pokeapi/PokeApi*ComponentTest.java` |
| **Intent** | Every upstream failure has a defined behaviour, and it is tested |
| **Depends on** | F3 |

**How**
WireMock stubs: 200 with a real recorded payload · 200 with a branching chain · 200 with
only non-English genera · 404 · 500 · delay beyond timeout · malformed JSON · 429.

**Avoid**
- Testing only the happy path. Every row of the upstream error matrix will occur in production

| Field | Value |
|---|---|
| **Produces** | Eight contract tests |
| **Verify** | `mvn -B verify` |
| **Pass when** | Malformed JSON raises a mapping exception, never an NPE |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] Eevee renders eight branches
- [ ] Concurrency never exceeds 16; a default page issues 21 upstream calls
- [ ] Every failure stub has a defined, tested outcome
- [ ] No retry on 4xx

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US01-3, AC-US01-5, AC-US01-6 |
| Decision | [ADR-0006](../adr/0006-redis-cache-pokeapi-fanout.md) |

## Blocks

WU-US01-C, WU-US02-A

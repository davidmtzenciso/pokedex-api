# WU-US01-C — List Use Case and Endpoint

| Field | Value |
|---|---|
| **Work Unit** | WU-US01-C |
| **Parent** | [WF-US01 Pokémon Enumeration](../workflows/WF-US01-pokemon-enumeration.md) |
| **Objective contribution** | The story's deliverable: `GET /v1/pokedex/pokemon` |
| **Estimate** | M |
| **Status** | done |

## Objective

The read path end to end — cache, fan-out, stale fallback, pagination policy, and the
endpoint that serves it.

## Entry Criteria

- WU-US01-A and WU-US01-B green; WU-000-B green (generated `PokemonApi`)

## Outputs

- `ListPokemonUseCase`, `PokemonController` list operation, component tests

---

## ▶ Activity Sequence

### C1 — `ListPokemonUseCase`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/ListPokemonUseCase.java` + tests |
| **Intent** | Cache first, upstream second, local replica as the fallback |
| **Depends on** | — (entry) |

**How**
Ask `CachePort`; on a miss call `PokemonCatalog` and write back with the TTL. On upstream
failure fall back to the local read model and set `stale = true`; with no local copy let
`UpstreamUnavailableException` propagate. Validate `page ≥ 0`, `1 ≤ size ≤ 100`, default
`size = 10`.

**Conventions**
- `@Service @Transactional`; never import `HttpStatus` → [spring patterns](../handbook/spring-patterns.md)

**Avoid**
- Clamping an oversized `size`. Reject with 400 — a clamped response lets a caller believe it received everything

| Field | Value |
|---|---|
| **Produces** | The use case |
| **Verify** | `mvn -B test -Dtest=ListPokemonUseCaseTest` |
| **Pass when** | Cache hit, cache miss, stale fallback, and hard failure each have a test |
| **On fail / Rollback** | — |

### C2 — Endpoint and pagination policy

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `web/…/controller/PokemonController.java`, `application.yaml` |
| **Intent** | Thin controller, explicit pagination contract |
| **Depends on** | C1 |

**How**
`implements PokemonApi`. `spring.data.web.pageable.default-page-size=10`,
`max-page-size=100`. Spring **clamps** by default — validate explicitly and return 400
`INVALID_PAGINATION` naming the cap.

**Avoid**
- Relying on the clamp. Silence is the failure mode

| Field | Value |
|---|---|
| **Produces** | The endpoint |
| **Verify** | `curl -s 'localhost:8080/api/v1/pokedex/pokemon?size=101' \| jq .code` |
| **Pass when** | `INVALID_PAGINATION`; `size=100` succeeds; no params yields 10 rows |
| **On fail / Rollback** | — |

### C3 — Story component tests

| Field | Value |
|---|---|
| **Type** | test |
| **Target** | `web/…/PokemonListComponentTest.java` |
| **Intent** | Prove the story, not the plumbing |
| **Depends on** | C2 |

**How**
Assert **every row** carries sprite, category, mass in kilograms, and a non-empty abilities
array. Assert the cold path issues 21 calls with at most 16 concurrent, and the warm path
zero. Assert both upstream-failure branches.

| Field | Value |
|---|---|
| **Produces** | Story-level proof |
| **Verify** | `mvn -B verify` |
| **Pass when** | AC-US01-1 through AC-US01-6 green |
| **On fail / Rollback** | — |

---

## Deviations, and why

| # | Change | Reason |
|---|---|---|
| 1 | **Read-through caching lives in the catalogue adapter, not this use case.** C1 said "Ask `CachePort`; on a miss call `PokemonCatalog`" | The cache keys WU-US01-B specifies are *upstream-resource* keys — `pokeapi:pokemon:{id}`, `pokeapi:species:{id}`, `pokeapi:page:{o}:{l}`. Caching at the use case would cache mapped domain aggregates instead, losing reuse of a species across pages and forcing the aggregate to be serialisable. The observable property is unchanged and asserted: cold 21, warm 0 |
| 2 | **`PokemonCatalog.totalCount()` removed; `fetchPage` returns `CatalogPage(rows, totalCount)`** | The upstream listing returns rows and count in one response. Two port methods meant two listing calls, so a "1 + 2N" page actually cost **22**. The component test caught it |
| 3 | **`CatalogExceptionHandler`, not `GlobalExceptionHandler`** | Three streams run in parallel and WU-AUTH owns that file. Spring supports several advice classes; [WU-US04-B](WU-US04-B-crud-endpoints.md) folds these rows into the full handler |
| 4 | **`UnavailableLocalReplica` is a temporary `@ConditionalOnMissingBean` stand-in** | This use case needs a `PokemonRepository` bean and the adapter is WU-US03-A. Without it the context does not start at all. Reads report "nothing stored" (so an outage propagates rather than being masked); **writes throw**. It disappears the moment the real adapter is on the context — delete the package when WU-US03-A merges |
| 5 | **`skipDefaultInterface` dropped; only `ApiUtil.java` generated as a supporting file** | Default interface methods return 501 for operations a stream has not implemented yet, which is what lets three streams share one generated contract without one of them having to stub the other two's endpoints |

`@Transactional` was **not** applied: the catalogue call is remote I/O, and holding a
database transaction across it is what exhausts the pool under a slow upstream.

## Exit Criteria

- [x] Default page returns 10 complete rows, each with sprite, category, mass in kg and abilities (AC-US01-1)
- [x] `size=101` rejected with `INVALID_PAGINATION` as `application/problem+json`, `size=100` accepted, `size` absent defaults to 10 (AC-US01-2)
- [x] Cold page 21 calls, warm page 0 (AC-US01-3, AC-US01-4)
- [x] No-replica branch returns **502**, never 500 (AC-US01-6)
- [~] Stale-replica branch (AC-US01-5) is proven at the use-case tier; the end-to-end path needs the replica adapter from WU-US03-A

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US01-1 … AC-US01-6 |

## Blocks

WU-US02-B

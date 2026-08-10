# WU-US01-C — List Use Case and Endpoint

| Field | Value |
|---|---|
| **Work Unit** | WU-US01-C |
| **Parent** | [WF-US01 Pokémon Enumeration](../workflows/WF-US01-pokemon-enumeration.md) |
| **Objective contribution** | The story's deliverable: `GET /v1/pokedex/pokemon` |
| **Estimate** | M |
| **Status** | not started |

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

## Exit Criteria

- [ ] Default page returns 10 complete rows (AC-US01-1)
- [ ] `size=101` rejected, `size=100` accepted (AC-US01-2)
- [ ] Cold page 21 calls, warm page 0 (AC-US01-3, AC-US01-4)
- [ ] Both upstream-failure branches behave (AC-US01-5, AC-US01-6)

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US01-1 … AC-US01-6 |

## Blocks

WU-US02-B

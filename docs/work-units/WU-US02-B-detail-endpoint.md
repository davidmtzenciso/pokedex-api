# WU-US02-B — Detail Use Case and Endpoint

| Field | Value |
|---|---|
| **Work Unit** | WU-US02-B |
| **Parent** | [WF-US02 Detailed View](../workflows/WF-US02-detailed-view.md) |
| **Objective contribution** | `GET /v1/pokedex/pokemon/{idOrName}` |
| **Estimate** | S |
| **Status** | done |

## Objective

Serve the full record, accepting either an id or a name.

## Entry Criteria

- WU-US02-A and WU-US01-C green

## Outputs

- `GetPokemonDetailUseCase`, the detail operation, component tests

---

## ▶ Activity Sequence

### B1 — `GetPokemonDetailUseCase`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/GetPokemonDetailUseCase.java` + tests |
| **Intent** | Cache-then-catalog, with the same stale fallback as the list |
| **Depends on** | — (entry) |

**How**
Resolve `idOrName` — numeric means id, otherwise a name lookup. Cache first, then three
upstream calls: pokemon, species, evolution chain. Fall back to the local replica with
`stale = true` on upstream failure.

**Avoid**
- Duplicating the cache-and-fallback logic from `ListPokemonUseCase`. Extract the shared policy if it appears twice

| Field | Value |
|---|---|
| **Produces** | The use case |
| **Verify** | `mvn -B test -Dtest=GetPokemonDetailUseCaseTest` |
| **Pass when** | Both id and name resolve; upstream 404 → `PokemonNotFoundUpstreamException` |
| **On fail / Rollback** | — |

### B2 — Detail endpoint

| Field | Value |
|---|---|
| **Type** | edit |
| **Target** | `web/…/controller/PokemonController.java` + component test |
| **Intent** | The story's deliverable |
| **Depends on** | B1 |

**How**
Override the generated detail method. Component test asserts artwork, six stats, a clean
description, and the full chain — with Eevee as a fixture.

| Field | Value |
|---|---|
| **Produces** | The endpoint |
| **Verify** | `mvn -B verify` |
| **Pass when** | AC-US02-1 … AC-US02-4 green |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [x] Artwork, six stats, two types, clean description, full evolution chain
- [x] Eevee shows eight branches **through the API**, not only the mapper
- [x] Unknown name → 404 `POKEMON_NOT_FOUND_UPSTREAM` as `problem+json`
- [x] Either an id or a name resolves; mass in kg and height in metres
- [x] A species with no evolution chain returns an empty list, not a 500

### On "extract the shared policy if it appears twice"

It did. `UpstreamOutagePolicy` now states the degradation rule once for both read paths.
The distinction it encodes is the one that matters: **only an outage falls back**. An absent
Pokemon is an answer, so a 404 propagates and is never masked by stale local data — asserted
by `should_not_fall_back_to_the_replica_when_upstream_simply_has_no_such_pokemon`.

A blank `idOrName` is a **400**, not a 404: the contract declares `minLength: 1`, so it is
malformed input rather than a Pokemon that does not exist.

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US02-1 … AC-US02-4 |

## Blocks

none

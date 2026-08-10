# WU-US02-B — Detail Use Case and Endpoint

| Field | Value |
|---|---|
| **Work Unit** | WU-US02-B |
| **Parent** | [WF-US02 Detailed View](../workflows/WF-US02-detailed-view.md) |
| **Objective contribution** | `GET /v1/pokedex/pokemon/{idOrName}` |
| **Estimate** | S |
| **Status** | not started |

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

- [ ] Artwork, six stats, clean description, full evolution chain
- [ ] Eevee shows eight branches through the API, not only the mapper
- [ ] Unknown name → 404 `POKEMON_NOT_FOUND_UPSTREAM` as `problem+json`

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US02-1 … AC-US02-4 |

## Blocks

none

# WF-US02 — Detailed View

> **User story**: *Users must be able to access comprehensive data for a chosen Pokémon, specifically viewing its image, core statistics, narrative description, and evolutionary lineage.*
> **Depends on**: [WF-000](WF-000-foundation.md), [WF-US01](WF-US01-pokemon-enumeration.md) (shares the catalog adapter and cache)
> **Delivers**: `GET /v1/pokedex/pokemon/{idOrName}`
> **Estimate**: M
> **Work units**: [WU-US02-A](../work-units/WU-US02-A-detail-mapping.md) · [WU-US02-B](../work-units/WU-US02-B-detail-endpoint.md)

---

## 1. Summary

One Pokémon, fully rendered. The interesting part is not the endpoint — it is the two
upstream shapes that quietly defeat a naive mapper: the description carries control
characters, and the evolution chain is a **recursive tree**, not a list.

---

## 2. Design decisions specific to this story

| # | Decision | Alternatives | Rationale | Consequence |
|---|---|---|---|---|
| 1 | Flatten the evolution tree into an edge list | Nested tree in the response | An edge list keeps the aggregate flat and makes acyclicity (F12) checkable | Clients reconstruct the tree if they want one |
| 2 | Normalise the description in the `Description` value object | Strip at the mapper, or client-side | One place, enforced by construction | No consumer ever sees `\n` or `\f` |
| 3 | Accept id **or** name in the path | Id only | The upstream API accepts both, and names are what humans have | One extra lookup branch |

---

## 3. Specification

| Verb | Path | Auth | Success | Errors |
|---|---|:---:|---|---|
| GET | `/v1/pokedex/pokemon/{idOrName}` | 🔓 public | 200 `PokemonDetailDTO` | 404, 502, 504 |

Response adds, over the summary shape:

| Field | Source | Trap |
|---|---|---|
| `officialArtworkUrl` | `sprites.other['official-artwork'].front_default` | |
| `stats[]` | `pokemon.stats[]` — all six | |
| `heightMetres` | `pokemon.height` ÷ 10 | upstream is decimetres (IA3) |
| `description` | `species.flavor_text_entries[]`, `en` | contains literal `\n` and `\f` (IA4) |
| `evolutionChain[]` | `/evolution-chain/{id}` → recursive `chain.evolves_to[]` | **a tree, not a list** (IA5) |
| `localizedNames[]` | `species.names[]` — 12 locales | seeds US03's proprietary field (IA6) |

---

## 4. Domain delta

Adds `EvolutionLink` as a child of the `Pokemon` aggregate — a flattened edge
`(fromPokeApiId, toPokeApiId, trigger, minLevel)`.

Constraint **F12**: `∀(a,b) ∈ evolves_to⁺: a ≠ b` — the evolution graph is acyclic.
Test: `EvolutionChainMapperTest`.

---

## 5. Flow

Detail reuses the cache-then-catalog path from
[sequence-list-page.md](../diagrams/sequence-list-page.md), with one detail call and one
species call rather than a fan-out, plus an evolution-chain call.

---

## 9.5 Error paths

| Condition | Response |
|---|---|
| `idOrName` not found upstream | 404 `POKEMON_NOT_FOUND_UPSTREAM` |
| Species has no English `genera` | Fail loudly — a mapping exception, never a Japanese fallback |
| Malformed upstream JSON | Mapping exception → 502, never an NPE |
| Upstream unreachable, replica exists | 200 with `stale: true` |

---

## 10. Acceptance criteria

**AC-US02-1**: Given any Pokémon, when its detail route is called, then the response carries
official artwork, **all six** base stats, a description free of `\n` and `\f`, and the full
evolution chain.

**AC-US02-2**: Given **Eevee** (chain 67), then **eight** evolution branches are returned. A
linear mapper truncates this, which is why it is the fixture.

**AC-US02-3**: Given Bulbasaur, then `massKilograms` is `6.9` and `heightMetres` is `0.7` —
not 69 and 7.

**AC-US02-4**: Given a non-existent name, then 404 `POKEMON_NOT_FOUND_UPSTREAM` as
`application/problem+json`.

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R7 | The mapper truncates branching evolution families | Med | Med | Y | Eevee is a required fixture in `EvolutionChainMapperTest` |
| R7b | Description renders with visible artefacts | Med | Low | Y | Normalisation in the VO constructor, with a test |

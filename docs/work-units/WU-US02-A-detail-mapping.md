# WU-US02-A — Species and Evolution Mapping

| Field | Value |
|---|---|
| **Work Unit** | WU-US02-A |
| **Parent** | [WF-US02 Detailed View](../workflows/WF-US02-detailed-view.md) |
| **Objective contribution** | The two upstream shapes that defeat a naive mapper |
| **Estimate** | M |
| **Status** | not started |

## Objective

Map the description and the evolution chain correctly — the parts of US02 that are not
simply "return more fields".

## Entry Criteria

- WU-US01-A green (the catalog adapter and its client exist)

## Outputs

- Evolution-tree flattening, description normalisation, localised-name seeding

---

## ▶ Activity Sequence

### A1 — Flatten the evolution tree

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `infrastructure/…/pokeapi/EvolutionChainMapper.java` + test |
| **Intent** | `chain.evolves_to[]` nests arbitrarily deep; a flat mapper silently truncates it |
| **Depends on** | — (entry) |

**How**
Recursive descent producing an edge list of `EvolutionLink(from, to, trigger, minLevel)`.
The recursion is a plain method; a stream handles the fan-out at each level.

**Patterns**
- `mapMulti` for the per-level fan-out → [stream API](../handbook/stream-api.md)

**Avoid**
- Assuming a linear three-stage chain. **Eevee has eight branches** and is the required fixture

| Field | Value |
|---|---|
| **Produces** | Edge-list flattening |
| **Verify** | `mvn -B test -Dtest=EvolutionChainMapperTest` |
| **Pass when** | Eevee (chain 67) yields **8** branches; F12 acyclicity holds |
| **On fail / Rollback** | — |

### A2 — Description, stats, and localised names

| Field | Value |
|---|---|
| **Type** | edit |
| **Target** | `PokeApiMapper` + tests |
| **Intent** | Absorb the remaining upstream quirks in one place |
| **Depends on** | — (entry) |

**How**
`flavor_text_entries[]` filtered to `en`, then through the `Description` value object which
strips `\n` and `\f` on construction. All six `stats[]`. `species.names[]` seeds
`localizedNames` with `source = UPSTREAM` — which is what makes US03's proprietary field
demo with real data.

**Avoid**
- Taking `flavor_text_entries[0]` or `genera[0]`. Both arrays are multilingual and the first entry is frequently Japanese

| Field | Value |
|---|---|
| **Produces** | Complete detail mapping |
| **Verify** | `mvn -B test -Dtest=PokeApiMapperTest` |
| **Pass when** | No control characters survive; 12 locales seeded; height 0.7 m for Bulbasaur |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] Eevee renders eight branches
- [ ] Descriptions carry no `\n` or `\f`
- [ ] `heightMetres` is decimetres ÷ 10

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US02-1, AC-US02-2, AC-US02-3 |

## Blocks

WU-US02-B

# WU-000-C — Domain Core (TDD)

| Field | Value |
|---|---|
| **Work Unit** | WU-000-C |
| **Parent** | [WF-000 Foundation](../workflows/WF-000-foundation.md) |
| **Objective contribution** | The whole domain model, framework-free, tests first |
| **Estimate** | L |
| **Status** | done |

## Objective

Build every value object, the `Pokemon` and `User` aggregates, the replication state
machine, the merge policy, and the ports — with tests written before implementations.

## Entry Criteria

- WU-000-A green
- The six IAR probes in [WF-000 §3.0](../workflows/WF-000-foundation.md) re-confirmed

## Inputs

| Input | Source | Used by |
|---|---|---|
| Value object table | [WF-000 §4.5](../workflows/WF-000-foundation.md) | C1 |
| Invariants I1–I11 | [WF-000 §4.4](../workflows/WF-000-foundation.md) | C1–C5 |
| State machine | [replication-state-machine.md](../diagrams/replication-state-machine.md) | C4 |

## Outputs

- `domain` package at ≥95% line coverage and ≥85% mutation score
- Ports declared, ready for the adapters in WF-AUTH, WF-US01, and WF-US03 to implement

---

## ▶ Activity Sequence

### C1 — Value objects, test-first

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/vo/*.java` + tests |
| **Intent** | An invalid instance must be unconstructable |
| **Depends on** | — (entry) |

**How**
All records. Validate in the **compact constructor** and normalise there too. `Mass`
holds hectograms and exposes `toKilograms()`; `Height` holds decimetres. `Description`
strips `\n` and `\f` on construction. `PasswordHash.toString()` returns `"***"`.

Write the test first, including the invalid cases — that is the point of the exercise.

**Conventions**
- Records for immutable data; defensive `List.copyOf` in compact constructors → [java patterns](../handbook/java-patterns.md)
- Unit conversion lives in the VO, never at a call site → [domain patterns](../handbook/design-patterns.md)

**Avoid**
- A bare `int mass` — it permits negatives everywhere and invites a second divide-by-ten

| Field | Value |
|---|---|
| **Produces** | 13 value objects |
| **Verify** | `mvn -B test` |
| **Pass when** | Every VO has a valid-case and an invalid-case test; `MassConversionTest` asserts 69 → 6.9 kg |
| **On fail / Rollback** | — |

### C2 — Domain exceptions

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/exception/*.java` |
| **Intent** | One type per failure mode, because each maps to a distinct `code` |
| **Depends on** | — (entry) |

**How**
`RuntimeException` subclasses carrying context as a `transient` field, not only in the
message. One per row of the error matrix.

**Conventions**
- Specific domain exceptions, never generic → [error handling](../handbook/error-handling.md)

| Field | Value |
|---|---|
| **Produces** | Exception hierarchy |
| **Verify** | `mvn -B test` |
| **Pass when** | Compiles; each carries its context accessor |
| **On fail / Rollback** | — |

### C3 — `ReplicationState` with guarded transitions

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/model/ReplicationState.java` + test |
| **Intent** | Make the illegal transition impossible rather than merely discouraged |
| **Depends on** | C2 |

**How**
Six states — `DRAFT`, `PENDING`, `SYNCED`, `CUSTOMIZED`, `STALE`, `FAILED`. `DELETE` is
terminal and removes the row, so there is **no `ARCHIVED`**. `transitionTo(next)` throws
`IllegalStateTransitionException` for any edge not on the diagram.

**Patterns**
- Exactly one edge writes replicated fields from upstream → [state machine](../diagrams/replication-state-machine.md)

**Avoid**
- An `isSynced` boolean — it gives no guarantee about who may write what → [ADR-0010](../adr/0010-hard-deletes.md)

| Field | Value |
|---|---|
| **Produces** | The state machine |
| **Verify** | `mvn -B test -Dtest=ReplicationStateTransitionTest` |
| **Pass when** | **Every legal edge and every illegal edge** is covered |
| **On fail / Rollback** | — |

### C4 — `Pokemon` and `User` aggregates

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/model/*.java` + tests |
| **Intent** | Invariants live in the aggregate, not in a controller |
| **Depends on** | C1, C3 |

**How**
`Pokemon` holds VOs, children, and `replicationState`. Cross-aggregate references are
**id-only** — `curatedBy` is a `UserId`. `addTag` enforces I4 (≤10, ≤30 chars,
case-insensitively distinct). Children are returned as immutable copies.

**Conventions**
- Id-only cross-aggregate references; one aggregate per transaction → [design patterns](../handbook/design-patterns.md)

| Field | Value |
|---|---|
| **Produces** | Two aggregates |
| **Verify** | `mvn -B test` |
| **Pass when** | One passing named test per invariant I1–I11 |
| **On fail / Rollback** | — |

> **The merge policy is not here.** `PokemonMergePolicy` belongs to the story it serves and
> is built in [WU-US03-B](WU-US03-B-sync-use-cases.md). This work unit stops at the
> aggregate that the policy operates on.

### C5 — Declare the ports

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/port/*.java` |
| **Intent** | The domain states what it needs; infrastructure supplies it |
| **Depends on** | C4 |

**How**
`PokemonRepository`, `UserRepository`, `PokemonCatalog`, `CachePort`, `PasswordHasher`,
`TokenIssuer`, `ClockPort`. Domain types in, domain types out. Interfaces only — no tests.

**Patterns**
- Ports and adapters; `ClockPort` so no test ever sleeps → [design patterns](../handbook/design-patterns.md)

| Field | Value |
|---|---|
| **Produces** | Seven port interfaces |
| **Verify** | `mvn -B test` |
| **Pass when** | Compiles with no framework import |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [x] Every invariant **testable at the domain tier** has a passing named test
- [x] Domain line coverage ≥ 95% — **100.00% line, 100.00% branch**
- [x] Mutation score ≥ 85% — **100% (218/218), test strength 100%**
- [x] No test uses `any()`; no test asserts `!= null`; no test sleeps
- [x] `git log --oneline` shows test commits preceding implementation commits

### Which invariants this work unit can actually close

The original criterion read "every invariant except I5". That overstated what a
framework-free domain can prove: §4.4 names a `*ComponentTest` in its own `Test` column for
four of them, and those need a database, Redis or a filter chain that does not exist yet.

| Invariant | Closed here | Test |
|---|:---:|---|
| I2 name ≤ 60, non-blank | yes | `ValueObjectTest$PokemonNameTest` |
| I3 mass, height positive | yes | `ValueObjectTest$MassTest`, `$HeightTest` |
| I4 ≤ 10 tags, distinct | yes | `PokemonTest$PokemonTagLimitTest` |
| I6 legal transitions only | yes | `ReplicationStateTransitionTest`, `PokemonTest$ReplicationInvariantTest` |
| I7 closed `Region` enum | yes | `ValueObjectTest$RegionTest` |
| I10 password never exposed | partly | `UserTest` covers `toString`; `@JsonIgnore` and the log audit are web-tier |
| I1 unique `pokeApiId` | no | DB partial unique index — WU-US03-A |
| I5 merge preserves proprietary | no | `PokemonMergePolicyTest` — WU-US03-B |
| I8 one live token per family | no | WU-AUTH-A |
| I9 cascade delete | no | WU-US03-A |
| I11 authenticated mutations | no | WU-AUTH-C |

`Pokemon.replaceReplicated` already encodes the aggregate half of I5 — the
`STALE → {SYNCED, CUSTOMIZED}` guard and that proprietary fields survive a replacement. The
**property test over generated field combinations** is still WU-US03-B's.

```bash
mvn -B test && make mutation
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC10, AC10b |
| Decision | [ADR-0010](../adr/0010-hard-deletes.md) |

## Blocks

Every story workflow — this is the widest fan-out in the DAG

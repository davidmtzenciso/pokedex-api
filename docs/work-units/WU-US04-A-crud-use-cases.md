# WU-US04-A — Local CRUD Use Cases

| Field | Value |
|---|---|
| **Work Unit** | WU-US04-A |
| **Parent** | [WF-US04 Local Data Modification](../workflows/WF-US04-local-data-modification.md) |
| **Objective contribution** | Create, read, update, and delete over the curated catalogue |
| **Estimate** | M |
| **Status** | not started |

## Objective

The write path, with optimistic locking and a `PATCH` restricted to fields the curator
actually owns.

## Entry Criteria

- WU-US03-A green (persistence)

## Outputs

- Five use cases, each with tests for its PRE violations

---

## ▶ Activity Sequence

### A1 — Read and list

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/{ListLocalPokemon,GetLocalPokemon}UseCase.java` + tests |
| **Intent** | The public read surface over the local catalogue |
| **Depends on** | — (entry) |

**How**
Filters `region`, `tag`, and `q` compose. Same pagination policy as US01 — default 10,
maximum 100, reject above.

**Avoid**
- Loading the full aggregate to render a list row. Use a projection → [persistence patterns](../handbook/persistence-patterns.md)

| Field | Value |
|---|---|
| **Produces** | Two read use cases |
| **Verify** | `mvn -B test` |
| **Pass when** | Filters compose; pagination metadata reflects the **filtered** total |
| **On fail / Rollback** | — |

### A2 — Create, update, delete

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/{Create,Update,Delete}LocalPokemonUseCase.java` + tests |
| **Intent** | Mutations with the defensive logic the story asks for |
| **Depends on** | A1 |

**How**
`Create` rejects a duplicate `pokeApiId` with `DuplicatePokemonException` → 409. `Update`
checks `cmd.version` and lets `OptimisticLockingFailureException` propagate → 412. Writing
any proprietary field transitions `SYNCED → CUSTOMIZED`. `Delete` removes the row; children
go by cascade.

**The command object contains only proprietary fields.** A `PATCH` naming a replicated field
is a 400 — the curator is not that field's authority, and the edit would be overwritten on
the next re-sync anyway.

**Conventions**
- Hard deletes; no tombstone → [ADR-0010](../adr/0010-hard-deletes.md)

**Avoid**
- Conflating 409 and 412. "That already exists" and "someone changed it since you read it" are different conditions and different client responses

| Field | Value |
|---|---|
| **Produces** | Three mutation use cases |
| **Verify** | `mvn -B test` |
| **Pass when** | Every PRE violation from [WF-000 §4.7](../workflows/WF-000-foundation.md) has a test asserting the exception type |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] Every PRE violation tested
- [ ] 409 and 412 are distinct and both covered
- [ ] A `PATCH` on a replicated field is a 400
- [ ] No test uses `any()`; mutation score ≥ 75% on `application`

```bash
mvn -B test && make mutation
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US04-1, AC-US04-4, AC-US04-6 |

## Blocks

WU-US04-B

# WU-US03-A — Persistence

| Field | Value |
|---|---|
| **Work Unit** | WU-US03-A |
| **Parent** | [WF-US03 Data Synchronization](../workflows/WF-US03-data-synchronization.md) |
| **Objective contribution** | The relational store behind `PokemonRepository` and `UserRepository` |
| **Estimate** | M |
| **Status** | not started |

## Objective

Flyway schema, JPA data models, and repository adapters — verified against a real Postgres,
not H2.

## Entry Criteria

- WU-000-C green (ports declared)
- Docker daemon running

## Inputs

| Input | Source | Used by |
|---|---|---|
| ERD | [entity-relationship.md](../diagrams/entity-relationship.md) | E1 |
| Persistence rules | [persistence patterns](../handbook/persistence-patterns.md) | E2–E4 |

## Outputs

- `V1__schema.sql`, `*DataModel` entities, two repository adapters, component tests

---

## ▶ Activity Sequence

### E1 — Flyway `V1__schema.sql`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `src/main/resources/db/migration/V1__schema.sql` |
| **Intent** | The schema the ERD describes, with the constraints that carry meaning |
| **Depends on** | — (entry) |

**How**
Nine tables. `version BIGINT NOT NULL` for optimistic locking. `created_at`/`updated_at`.
**No `deleted_at`** — deletes are hard. Children carry `ON DELETE CASCADE`. The partial
unique index:

```sql
CREATE UNIQUE INDEX ux_pokemon_poke_api_id
    ON pokemon (poke_api_id) WHERE poke_api_id IS NOT NULL;
```

**Conventions**
- Never edit an applied migration; add a corrective one → [persistence patterns](../handbook/persistence-patterns.md)

**Avoid**
- A plain unique constraint on `poke_api_id` — `DRAFT` rows have none, so only one could exist

| Field | Value |
|---|---|
| **Produces** | `V1__schema.sql` |
| **Verify** | Component test boots against a fresh Testcontainer |
| **Pass when** | Flyway applies cleanly from empty |
| **On fail / Rollback** | Fix the migration; in dev, `docker compose down -v` |

### E2 — `*DataModel` entities

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `infrastructure/…/persistence/model/*DataModel.java` |
| **Intent** | JPA shapes, kept strictly inside infrastructure |
| **Depends on** | E1 |

**How**
Data only — no behaviour. `@Version` on both roots. Children mapped with
`cascade = ALL, orphanRemoval = true`. `@EntityGraph` on the list query so a page issues one
query, not 41.

**Conventions**
- Three shapes never confused: domain, `*DataModel`, `*DTO` → [persistence patterns](../handbook/persistence-patterns.md)

**Avoid**
- Business methods on a `*DataModel`; ArchUnit `N4`/`IO1` confine it, and the domain object exists for that

| Field | Value |
|---|---|
| **Produces** | Nine entities |
| **Verify** | `mvn -B verify` |
| **Pass when** | Schema validation passes against the migrated database |
| **On fail / Rollback** | — |

### E3 — Repository adapters and mappers

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `…/persistence/{JpaPokemonRepositoryAdapter,JpaUserRepositoryAdapter}.java` + mappers |
| **Intent** | Satisfy the ports with domain types in and domain types out |
| **Depends on** | E2 |

**How**
Spring Data interfaces plus adapters implementing the domain ports. The adapter maps at the
boundary; a JPA proxy never escapes infrastructure.

**Patterns**
- Adapter-side mapper → [design patterns](../handbook/design-patterns.md)

| Field | Value |
|---|---|
| **Produces** | Two adapters |
| **Verify** | `mvn -B verify` |
| **Pass when** | Adapters compile against the ports with no signature change |
| **On fail / Rollback** | — |

### E4 — Component tests on real Postgres

| Field | Value |
|---|---|
| **Type** | test |
| **Target** | `…/persistence/*ComponentTest.java` |
| **Intent** | Verify the behaviour H2 would fake |
| **Depends on** | E3 |

**How**
Testcontainers Postgres 17. Cover: the partial unique index rejecting a duplicate
`pokeApiId` while permitting many `DRAFT` rows; cascade delete leaving **no orphans**;
optimistic-lock conflict under concurrent update.

**Avoid**
- H2. Its dialect differs enough that a green suite can hide a broken production query → [testing pyramid](../handbook/testing-pyramid.md)

| Field | Value |
|---|---|
| **Produces** | Persistence component tests |
| **Verify** | `mvn -B verify` |
| **Pass when** | `PokemonCascadeDeleteComponentTest` and `PokemonUniquenessComponentTest` green |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] Flyway applies cleanly from empty
- [ ] Cascade delete leaves no orphans (F10)
- [ ] Concurrent update produces an optimistic-lock failure, not a lost update
- [ ] A page of 10 issues **one** query — check `hibernate.generate_statistics`

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC1, AC3e, AC11 |
| Decision | [ADR-0010](../adr/0010-hard-deletes.md) |

## Blocks

WU-US03-B, WU-US04-A

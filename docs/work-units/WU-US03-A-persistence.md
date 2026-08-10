# WU-US03-A — Persistence

| Field | Value |
|---|---|
| **Work Unit** | WU-US03-A |
| **Parent** | [WF-US03 Data Synchronization](../workflows/WF-US03-data-synchronization.md) |
| **Objective contribution** | The relational store behind `PokemonRepository` and `UserRepository` |
| **Estimate** | M |
| **Status** | done |

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

Set `spring.jpa.hibernate.ddl-auto: validate` in the same change. Flyway owns the schema;
Hibernate checks the entities against it and refuses to boot on a mismatch, but never
modifies it — [ADR-0012](../adr/0012-flyway-versioned-migrations.md).

**Conventions**
- Never edit an applied migration; add a corrective one → [persistence patterns](../handbook/persistence-patterns.md)

**Avoid**
- A plain unique constraint on `poke_api_id` — `DRAFT` rows have none, so only one could exist
- Any `ddl-auto` value that writes (`update`, `create`, `create-drop`). It cannot generate the partial index, so the schema would be silently wrong

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

- [x] Flyway applies cleanly from empty — `SchemaMigrationComponentTest`
- [x] Cascade delete leaves no orphans (F10) — `PokemonCascadeDeleteComponentTest`
- [x] Concurrent update produces an optimistic-lock failure, not a lost update — `PokemonOptimisticLockingComponentTest`
- [x] A page of 10 issues **seven** queries, not one — `PokemonPageQueryCountComponentTest`

```bash
mvn -B verify
```

### Departures from this work unit, and why

**A page of ten issues seven queries, not one.** A `Pokemon` has six child collections.
Join-fetching them together under a `limit` either multiplies their rows into a cartesian
product or makes Hibernate paginate in memory — fetching the whole table to return ten.
Fetching none of them is the N+1: `1 + 6×10 = 61`. `@BatchSize` takes the third option, one
query for the page and one per collection for the whole page, so the count is bounded by the
**number of collections rather than the page size**. That is the property "no N+1" names;
`should_issue_the_same_number_of_queries_when_the_page_is_five_times_larger` is the assertion
that a page of fifty still costs seven.

**`users` has no `version` column and `UserDataModel` no `@Version`**, against E2's
"`@Version` on both roots". The [ERD](../diagrams/entity-relationship.md) gives `version` to
`POKEMON` alone and the domain `User` carries none — so a caller has no version to send back
and nothing could ever be checked against it. A column that cannot be checked is the
appearance of optimistic locking without the fact, and the difference only ever shows up as
a lost update nobody notices.

**Eight entities, not nine.** `refresh_tokens` is in `V1__schema.sql` because `db/migration`
has a single writer and a second one would produce two `V2`s. `RefreshTokenDataModel` is not,
because `RefreshToken` and its family-rotation rule are
[WU-AUTH-A](WU-AUTH-A-user-domain.md)'s aggregate — an entity with no domain type to map to
would be a guess at a shape, with no test that could tell whether the guess was right.

**No unique constraint on a child's natural key.** `ux_pokemon_tag_label` was written, and
removed once the component tier proved it made every second save fail: children are replaced
wholesale (WF-000 §3.1), so a kept tag is deleted and reinserted in one flush, and Hibernate
orders the insert first. Postgres cannot defer an expression index. F4 stays where it was
already enforced, in `Pokemon.addTag`.

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC1, AC3e, AC11 |
| Decision | [ADR-0010](../adr/0010-hard-deletes.md) |

## Blocks

WU-US03-B, WU-US04-A

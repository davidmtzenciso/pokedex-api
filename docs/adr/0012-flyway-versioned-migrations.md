# ADR-0012: Flyway Versioned Migrations, Not `ddl-auto`

**Status**: Accepted
**Date**: 2026-08-10
**Deciders**: David Martinez

## Context

The schema has to come from somewhere. Spring Boot offers this for free:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

Hibernate reads the `*DataModel` entities at boot and emits the DDL it thinks they imply.
Zero files, zero configuration, and for a single-environment exercise it would start
cleanly on the first run. It is the path of least resistance and it deserves a real
hearing rather than a reflexive dismissal.

Three things make it the wrong answer **here specifically**, and none of them is "because
production".

**The schema contains something Hibernate cannot express.** `DRAFT` records are created
locally and have no `pokeApiId` until they are synced. The uniqueness rule is therefore
conditional:

```sql
CREATE UNIQUE INDEX ux_pokemon_poke_api_id
    ON pokemon (poke_api_id) WHERE poke_api_id IS NOT NULL;
```

There is no JPA annotation for a partial index. `@Column(unique = true)` generates a plain
unique constraint, and because SQL treats `NULL`s as distinct in *some* engines and not
others, the behaviour is both wrong and dialect-dependent. Under `ddl-auto` this surfaces
as the second `DRAFT` record failing to save — a long way from the annotation that caused
it.

**The demo needs data before anyone logs in.** WF-999 requires `docker compose up --build`
to reach a demoable API with **zero manual steps**, which means the original 151 Pokémon
and the demo users must exist by the time the first request arrives. A migration is a
natural home for that. The alternative is a script someone has to remember to run, and the
failure mode of a forgotten step is an empty database in front of an audience.

**The component tests would otherwise verify a schema that never ships.** Testcontainers
boots a real Postgres per WF-US03-A. If Hibernate generates the schema at test time, the
tests exercise Hibernate's opinion rather than the artifact deployed to any other
environment — and the one thing a component test exists to catch is exactly that gap.

## Decision

**Flyway owns the schema. `ddl-auto` is set to `validate`.**

```
V1__schema.sql          tables, indexes, constraints
V2__seed.sql            the original 151 + demo users
V3__*.sql               every subsequent change is a new file
```

- `spring.jpa.hibernate.ddl-auto: validate` — Hibernate may **check** that the entities match the schema and refuse to boot if they do not. It may never modify it.
- Migrations are plain SQL, not Java. Nothing in `V1` needs application logic, and SQL is reviewable by anyone.
- **An applied migration is never edited.** Flyway hashes each file and fails fast on a checksum mismatch. Corrections are new files. In development the reset is `docker compose down -v`.
- Migrations run at boot, before the context is ready. The container healthcheck's `start-period=40s` covers this — see [containerization](../handbook/containerization.md).

`validate` is the part that earns its keep quietly: it makes entity/schema drift a startup
failure with a precise message, instead of a `column does not exist` at whatever hour the
first request touches that field.

## Alternatives Considered

1. **`ddl-auto: update`** — Free, no files, no ordering to reason about. Rejected on the partial index alone: it cannot generate one, so the schema would be silently wrong in a way that only appears on the second `DRAFT` record. Beyond that, `update` is additive-only — it adds columns and never drops or narrows them — so environments diverge quietly and nobody can say what the schema *is* without inspecting a database.

2. **`ddl-auto: create-drop` plus a seed script** — Honest about being throwaway, and fine for tests. Rejected as the production path for the same partial-index reason, and because it makes the schema unversioned: there is no artifact to review in a pull request, and no way to see what changed between two commits.

3. **Liquibase** — Equivalent capability, arguably better at rollback and at abstracting across engines. Rejected because its XML/YAML changelog is a second dialect to learn for a schema that is one PostgreSQL file, and this project needs no cross-engine portability. Flyway's plain-SQL migrations are readable by someone who knows only SQL, which is the wider audience.

4. **Hand-applied SQL, documented in the README** — Rejected outright. This is `ddl-auto`'s ordering problem plus a human in the loop, and it breaks the zero-manual-steps requirement directly.

5. **Flyway for schema, `import.sql` for seed data** — Hibernate's `import.sql` runs after schema creation and would keep migrations "pure". Rejected because it splits the same concern across two mechanisms with different ordering guarantees, and `import.sql` is tied to `ddl-auto` being active — which it is not.

## Consequences

### Positive
- The schema is a reviewable artifact. A pull request shows exactly what changed, in SQL, before it touches a database.
- Partial indexes, check constraints, and anything else PostgreSQL supports are available, because we are writing SQL rather than hoping an ORM emits it.
- `validate` turns entity/schema drift into a startup failure with a clear message.
- Component tests run the migrations that ship. The schema under test and the schema deployed are the same bytes.
- Seeding is part of startup, so a cold `docker compose up --build` is genuinely one command.

### Negative
- **Every schema change is now two edits** — the entity and the migration — and they can disagree. `validate` catches the disagreement at boot rather than at write time, but it is still a real tax on the inner loop that `ddl-auto` does not charge.
- **An applied migration cannot be edited**, which is unforgiving during development. Getting `V1` wrong on the fifth iteration means `docker compose down -v` and losing local data. This is the correct behaviour and it is still friction.
- Two more dependencies (`flyway-core`, `flyway-database-postgresql`) and a version to keep current.
- Migrations run before the application context is ready, so a slow one delays readiness. The healthcheck's `start-period` has to account for it, and too short a value kills the container mid-migration.

### Neutral
- Rollback is forward-only by convention: we add a corrective migration rather than running a down-script. Flyway's `undo` is a paid feature and we do not rely on it.
- Migrations are SQL today. If one ever needs application logic — a backfill requiring domain rules — Flyway supports Java migrations, and that would be the point to reconsider, not now.
- If this project ever needed to support a second database engine, alternative 3 becomes materially more attractive. It does not.

## Related

[persistence patterns](../handbook/persistence-patterns.md) (the conventions) ·
[WU-US03-A](../work-units/WU-US03-A-persistence.md) (the activities) ·
[ADR-0010](0010-hard-deletes.md) (why the only partial index is the `DRAFT` one) ·
[troubleshooting](../guides/troubleshooting.md) (checksum mismatch)

# ADR-0010: Deletes Are Hard

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

`DELETE /v1/pokedex/local/{id}` has to do something. The reflexive enterprise answer is a soft delete: a nullable `deleted_at` column, `@SQLDelete` rewriting the statement into an update, and `@SQLRestriction("deleted_at IS NULL")` filtering it back out of every read.

An earlier draft of this design did exactly that, and it is defensible — soft delete buys recoverability and a crude audit trail, and it is what most production systems eventually want.

It also has a cost that is easy to under-price. Every unique constraint needs an `AND deleted_at IS NULL` clause. Every hand-written query has to remember the predicate. Every join has to remember it on both sides. And the failure mode is silent: one forgotten filter resurrects deleted data into a list view, and nothing fails — the row simply reappears.

The question is whether this project has a requirement that pays for that.

It does not. Nothing in the brief asks for recoverable deletes, undo, or audit history. And the data being deleted is, uniquely, **recreatable on demand**: a deleted Pokémon can be restored with one `POST /sync/{id}`, because PokeAPI is still the system of record.

## Decision

`DELETE` removes the row.

- No `deleted_at` column on any table.
- No `@SQLDelete`, no `@SQLRestriction`, no filter predicate in any query.
- Children — abilities, stats, types, tags, localized names, evolution links — are removed by JPA cascade with `orphanRemoval = true`.
- The `ARCHIVED` state disappears from the replication state machine, which drops from seven states to six. `DELETE` is a terminal transition from any state.
- Restoring a deleted Pokémon means re-syncing it. That enters the state machine at `PENDING` and produces a genuinely new record with a new id — which is the honest description of what happened.

The one remaining partial index is unrelated to deletion and stays:

```sql
CREATE UNIQUE INDEX ux_pokemon_poke_api_id
    ON pokemon (poke_api_id) WHERE poke_api_id IS NOT NULL;
```

`DRAFT` records have no `pokeApiId`, so a plain unique constraint would permit at most one of them.

## Alternatives Considered

1. **Soft delete with `deleted_at` and `@SQLRestriction`** — Recoverable, and a partial audit trail for free. Rejected because nothing requires either, and the cost is paid on every query in the codebase forever. The silent failure mode is the deciding factor: a forgotten predicate does not throw, it just shows deleted data.
2. **Soft delete on `Pokemon` only, hard delete elsewhere** — Cheaper, and targets the one entity a curator might delete by accident. Rejected because a mixed policy is worse than either pure one: a reader now has to know, per table, which rules apply, and the inconsistency is exactly where mistakes happen.
3. **Hard delete plus an append-only audit log** — What we would do if auditing were required. Rejected as premature: it is real work for a requirement that does not exist. Recording it here means the successor decision is obvious if it ever does.
4. **Archive to a separate `pokemon_archive` table on delete** — Preserves history without polluting the live schema. Rejected for the same reason as 3, with the extra cost of keeping two schemas in step through every migration.

## Consequences

### Positive
- Every query means what it says. No implicit predicate, no forgotten filter, no resurrected rows.
- Unique constraints are simple; only the `DRAFT` case needs a partial index, and for an unrelated reason.
- One fewer state in the domain model, one fewer invariant, one fewer component test.
- The schema matches the requirements exactly rather than anticipating ones nobody asked for.

### Negative
- **A delete is irreversible.** There is no undo, and no "restore" endpoint. For replicated data this is mitigated by re-sync; for a curator's **proprietary** fields — region, notes, tags — it is genuine, permanent data loss. That is the real cost of this decision and it should be named plainly.
- No audit trail of what was deleted or by whom.
- A client should confirm before deleting, since the operation cannot be walked back. That is now a UI obligation rather than something the backend can soften.

### Neutral
- Cascade behaviour must be correct and tested, because there is no tombstone to catch an orphan. `PokemonCascadeDeleteComponentTest` covers it.
- If auditing becomes a requirement, alternative 3 is the successor — an append-only log, not a nullable column that changes the meaning of every `SELECT`.

# WF-US03 — Data Synchronization

> **User story**: *Develop a mechanism to persist Pokémon data into a local relational store. This replication layer is intended to facilitate the addition of proprietary fields — localized nomenclature, geographical metadata, or internal classification tags.*
> **Depends on**: [WF-000](WF-000-foundation.md), [WF-AUTH](WF-AUTH-user-management.md) (sync is a protected operation)
> **Delivers**: the persistence layer, `POST /v1/pokedex/sync/{idOrName}`, `POST /v1/pokedex/sync/batch`
> **Estimate**: L
> **Work units**: [WU-US03-A](../work-units/WU-US03-A-persistence.md) · [WU-US03-B](../work-units/WU-US03-B-sync-use-cases.md) · [WU-US03-C](../work-units/WU-US03-C-sync-endpoints.md)

---

## 1. Summary

The story asks for replication *so that* proprietary fields can be added. That "so that" is
the whole design problem, and the brief does not state it: **what happens to a curator's
edits when the record is re-synchronised?**

The answer is a disjoint field partition. Every field belongs to exactly one of two sets —
`Replicated` (authority: PokeAPI) or `Proprietary` (authority: the curator) — and because
`Proprietary ∩ Replicated = ∅`, re-sync needs no conflict resolution at all. The conflict
was designed out rather than handled.

---

## 2. Design decisions specific to this story

| # | Decision | Alternatives | Rationale | Consequence |
|---|---|---|---|---|
| 1 | Disjoint field partition; re-sync preserves proprietary fields | Last-write-wins; block re-sync once customised | Curator data is the only irreplaceable data — upstream can always be re-fetched | F7, AC5 — [ADR-0007](../adr/0007-proprietary-field-merge-policy.md) |
| 2 | Proprietary fields on the `Pokemon` aggregate | Separate annotation entity; JSONB blob | Typed, validatable, queryable; a blob makes `region` and `tags` neither | A schema change when a new family appears |
| 3 | Seed `localizedNames` from upstream `species.names[]` | Curator authors them from scratch | 12 locales arrive free, so the story demos with real data | The `source` discriminator on the entity |
| 4 | Replication is a state machine, not a boolean | `isSynced` flag | Exactly one edge writes replicated fields, so the guarantee is structural | Six states, `DELETE` terminal |
| 5 | Batch capped at 200 ids, 202 with a partial summary | Unbounded; 200 or 500 | Partial success is the expected outcome against a public API | `failedIds` returned for a targeted re-run |

---

## 3. Specification

| Verb | Path | Auth | Success | Errors |
|---|---|:---:|---|---|
| POST | `/v1/pokedex/sync/{idOrName}` | 🔒 | 201 new / 200 refreshed | 401, 404, 409, 412, 502 |
| POST | `/v1/pokedex/sync/batch` | 🔒 | 202 + summary | 400, 401, 429 |

Batch body `{from, to}`; `to − from ≤ 200`. Response
`{succeeded, failed, skipped, failedIds[]}` — the three counts partition the requested range.

### The field partition

| Set | Fields | Authority |
|---|---|---|
| `Replicated` | name, category, mass, height, baseExperience, spriteUrl, description, abilities, stats, types, evolutionLinks, `localizedNames[source = UPSTREAM]` | PokeAPI |
| `Proprietary` | **region**, **notes**, **tags**, `localizedNames[source = CURATOR]`, curatedBy | The curator |

These map directly onto the story's three named families: *localized nomenclature*
(`localizedNames`), *geographical metadata* (`region`), *internal classification tags*
(`tags`).

---

## 4. Domain delta

Owns the persistence of the `Pokemon` aggregate and its children, plus:

- **`PokemonMergePolicy`** — pure, implementing F7
- **`ReplicationState`** — the six-state machine ([diagram](../diagrams/replication-state-machine.md))
- Invariants **I1** (`pokeApiId` unique when non-null), **I4** (≤ 10 tags), **I5** (re-sync preserves proprietary), **I6** (legal transitions), **I9** (cascade delete leaves no orphans)

Formal constraints: **F1**, **F5**, **F6**, **F7**, **F10** — see
[WF-000 §4.7](WF-000-foundation.md).

---

## 5. Flow

[sequence-resync-merge.md](../diagrams/sequence-resync-merge.md) ·
[data-flow-sync.md](../diagrams/data-flow-sync.md) ·
[replication-state-machine.md](../diagrams/replication-state-machine.md)

---

## 9.5 Error paths

[error-paths-resync.md](../diagrams/error-paths-resync.md) ·
[error-paths-batch-sync.md](../diagrams/error-paths-batch-sync.md)

| Condition | Response |
|---|---|
| `pokeApiId` already replicated | 409 `DUPLICATE_POKEMON` |
| Re-sync on a record not in `STALE`/`FAILED` | 409 `ILLEGAL_STATE_TRANSITION` |
| Upstream unreachable during sync | state → `FAILED`, 502 `UPSTREAM_UNAVAILABLE` |
| Concurrent edit during a slow upstream call | 412 `STALE_VERSION` |
| Batch range > 200 | 400 `BATCH_RANGE_TOO_LARGE` |
| Batch partially fails | **202**, never 500 — with `failedIds` |

---

## 10. Acceptance criteria

**AC-US03-1**: Given an authenticated curator, when `POST /sync/1` is called, then a local
record is created in state `SYNCED` with `syncedAt` set, `localizedNames` seeded from
`species.names[]`, and 201 with a `Location` header.

**AC-US03-2 (AC5)**: Given a `CUSTOMIZED` record with `region`, `notes`, and 3 tags, when
re-sync runs against **changed** upstream data, then every replicated field updates **and
every proprietary field is byte-identical to its prior value**.

**AC-US03-3**: Given a record with no proprietary fields, when re-sync runs, then it
transitions `STALE → SYNCED`; with proprietary fields, `STALE → CUSTOMIZED`.

**AC-US03-4**: Given a batch of 200 where some ids do not exist upstream, then 202 returns
with counts that **partition** the range and `failedIds` listing the failures.

**AC-US03-5**: Given an unauthenticated request to any sync endpoint, then 401 and **no row
is written**.

**AC-US03-6**: Given a `DELETE` on a synced record, then the row **and every child** are
removed, and re-syncing produces a new record with a new id.

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R6 | The merge has a hole and re-sync silently destroys curator data | Low | High | Y | F7 is a **property** test over generated field combinations, backed by a PIT threshold of 85% on `domain` |
| R2 | Batch sync gets us rate-limited | Med | High | R | 200-id cap, `Semaphore(16)`, backoff, resumable via `failedIds` |
| R11 | A new field is added to neither partition and is silently dropped on re-sync | Med | Med | Y | Both sets are named constants that the property test enumerates — a field in neither fails the build |

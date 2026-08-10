# WU-US03-C — Sync Endpoints and Seed Data

| Field | Value |
|---|---|
| **Work Unit** | WU-US03-C |
| **Parent** | [WF-US03 Data Synchronization](../workflows/WF-US03-data-synchronization.md) |
| **Objective contribution** | The story's HTTP surface, and data to demonstrate it with |
| **Estimate** | M |
| **Status** | not started |

## Objective

Expose sync as protected operations, and seed the catalogue so the proprietary-field story
demos against real data.

## Entry Criteria

- WU-US03-B green, WU-AUTH-C green (sync is protected)

## Outputs

- `SyncController`, `V2__seed.sql`, story component tests

---

## ▶ Activity Sequence

### C1 — `SyncController`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `web/…/controller/SyncController.java` |
| **Intent** | Two protected operations, thin |
| **Depends on** | — (entry) |

**How**
`implements SyncApi`. Single sync returns 201 with `Location` when new, 200 when refreshed.
Batch returns **202** with the summary.

**Avoid**
- 200 for batch. The work is accepted and partially complete, and 202 says so

| Field | Value |
|---|---|
| **Produces** | The endpoints |
| **Verify** | `mvn -B test` |
| **Pass when** | Slice tests green; both are 401 unauthenticated |
| **On fail / Rollback** | — |

### C2 — `V2__seed.sql`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `src/main/resources/db/migration/V2__seed.sql` |
| **Intent** | US03 is unconvincing against empty proprietary fields |
| **Depends on** | — (entry) |

**How**
The original 151 with children, and **populated** `region`, `notes`, and `tags` on a
representative subset. Users `demo` and `admin` with BCrypt hashes. Seeding also removes the
cold-path latency that is risk R1.

**Avoid**
- Seeding a plaintext password. Hash it in the migration

| Field | Value |
|---|---|
| **Produces** | Demo data |
| **Verify** | `curl -s localhost:8080/api/v1/pokedex/local \| jq .totalElements` |
| **Pass when** | 151; proprietary fields visibly populated |
| **On fail / Rollback** | `docker compose down -v` |

### C3 — Story component tests

| Field | Value |
|---|---|
| **Type** | test |
| **Target** | `web/…/SyncComponentTest.java` |
| **Intent** | Prove the merge through the API, not only in a unit test |
| **Depends on** | C1, C2 |

**How**
**The AC5 test is the important one**: create a `CUSTOMIZED` record with region, notes, and
three tags; re-sync against changed upstream data; assert every proprietary field is
byte-identical **and** every replicated field changed.

Also: 401 unauthenticated with no row written; `DELETE` removes the row and every child.

| Field | Value |
|---|---|
| **Produces** | Story-level proof |
| **Verify** | `mvn -B verify` |
| **Pass when** | AC-US03-1 … AC-US03-6 green |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] Sync creates in `SYNCED` with seeded localised names (AC-US03-1)
- [ ] Re-sync preserves proprietary fields through the API (AC-US03-2)
- [ ] Batch returns 202 with a partitioning summary (AC-US03-4)
- [ ] Unauthenticated sync is 401 with no write (AC-US03-5)

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US03-1 … AC-US03-6 |

## Blocks

WU-US04-A

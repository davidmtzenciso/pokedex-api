# WU-US03-B — Sync Use Cases and the Merge Policy

| Field | Value |
|---|---|
| **Work Unit** | WU-US03-B |
| **Parent** | [WF-US03 Data Synchronization](../workflows/WF-US03-data-synchronization.md) |
| **Objective contribution** | The rule that makes replication safe |
| **Estimate** | L |
| **Status** | not started |

## Objective

Sync, re-sync, and batch sync — with a merge that provably cannot lose curator data.

## Entry Criteria

- WU-US03-A green (persistence), WU-US01-A green (catalog adapter)

## Outputs

- `PokemonMergePolicy` property-tested, three sync use cases

---

## ▶ Activity Sequence

### B1 — `PokemonMergePolicy` as a property test

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/policy/PokemonMergePolicy.java` + property test |
| **Intent** | Prove F7 for **every** field combination, not one example |
| **Depends on** | — (entry) |

**How**
Name `Proprietary` and `Replicated` as explicit constants; the property test enumerates
them. `merge(existing, upstream)` takes replicated fields from upstream and proprietary
fields from existing. Pure — no repository, no clock, no Spring.

**The test must assert both directions.** A test that only checks preservation passes when
the merge does nothing at all.

**Patterns**
- Policy object; disjoint partition → [ADR-0007](../adr/0007-proprietary-field-merge-policy.md)

**Avoid**
- An example test whose fixture has **empty** proprietary fields — "preserved" and "cleared" then look identical. That is the exact mutant that survives PIT → [testing pyramid](../handbook/testing-pyramid.md)

| Field | Value |
|---|---|
| **Produces** | The merge policy |
| **Verify** | `mvn -B test -Dtest=PokemonMergePolicyTest && make mutation` |
| **Pass when** | Property test green; **no surviving mutant** in the policy |
| **On fail / Rollback** | A survivor here is a real data-loss gap — fix the test first, then the code |

### B2 — Sync and re-sync use cases

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/{SyncPokemonUseCase,ReSyncPokemonUseCase}.java` + tests |
| **Intent** | Replication with a state guard before the network call |
| **Depends on** | B1 |

**How**
`ReSync` guards on state ∈ {`STALE`, `FAILED`} **before** spending an upstream request, then
delegates the field merge to the policy. Outcome is `SYNCED` when no proprietary fields are
present, `CUSTOMIZED` when they are. Sync seeds `localizedNames` from `species.names[]` with
`source = UPSTREAM`.

**Avoid**
- Re-implementing the merge inline. The policy is pure precisely so it can be property-tested

| Field | Value |
|---|---|
| **Produces** | Two use cases |
| **Verify** | `mvn -B test` |
| **Pass when** | Both merge branches tested; the state guard rejects before any network call |
| **On fail / Rollback** | — |

### B3 — `BatchSyncUseCase`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/BatchSyncUseCase.java` + test |
| **Intent** | Partial success is the expected outcome, so model it |
| **Depends on** | B2 |

**How**
Cap the range at 200 ids. Fan out through the bounded executor. Partition outcomes into
succeeded, failed, and skipped — the three **partition** the requested range. Return
`failedIds` so a re-run targets only the remainder. One backoff-and-requeue on 429, then
count as failed.

**Avoid**
- Returning 500 on partial failure, or retrying a rate limiter forever

| Field | Value |
|---|---|
| **Produces** | Batch sync |
| **Verify** | `mvn -B test -Dtest=BatchSyncSummaryTest` |
| **Pass when** | The three counts sum to the requested range; `failedIds` populated |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] AC5: re-sync preserves every proprietary field byte-for-byte
- [ ] Mutation score ≥ 85% on `domain`
- [ ] Batch returns 202 with a partitioning summary
- [ ] No test uses `any()`

```bash
mvn -B test && make mutation
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US03-2, AC-US03-3, AC-US03-4 |
| Decision | [ADR-0007](../adr/0007-proprietary-field-merge-policy.md) |

## Blocks

WU-US03-C

# WU-999-B — Verification

| Field | Value |
|---|---|
| **Work Unit** | WU-999-B |
| **Parent** | [WF-999 Delivery](../workflows/WF-999-delivery.md) |
| **Objective contribution** | Proof that every acceptance criterion holds, and an honest record of what does not |
| **Estimate** | S |
| **Status** | not started |

## Objective

Run every gate on a clean clone, walk each acceptance criterion with evidence, and write a
report that is honest about gaps.

## Entry Criteria

- WU-000-D and WU-999-A green

## Inputs

| Input | Source | Used by |
|---|---|---|
| AC list | every workflow's §10 | K3 |
| IAR probes | [WF-000 §3.0](../workflows/WF-000-foundation.md) | K4 |
| Report shape | [WF-999 §11](../workflows/WF-999-delivery.md) | K5 |

## Outputs

- `docs/execution-reports/pokedex-api-execution-report.md`

---

## ▶ Activity Sequence

### K1 — Clean-clone verify

| Field | Value |
|---|---|
| **Type** | verify |
| **Target** | a fresh clone in a temp directory |
| **Intent** | Prove it works somewhere that is not your machine |
| **Depends on** | — (entry) |

**How**
Clone to `/tmp`, run `make verify` from nothing. This catches the uncommitted file, the
`~/.m2` artifact nobody else has, and the environment variable only you export.

| Field | Value |
|---|---|
| **Produces** | Clean-clone evidence |
| **Verify** | `git clone . /tmp/verify && cd /tmp/verify && make verify` |
| **Pass when** | Green with no manual intervention |
| **On fail / Rollback** | Commit whatever was missing |

### K2 — Coverage and mutation

| Field | Value |
|---|---|
| **Type** | verify |
| **Target** | JaCoCo and PIT reports |
| **Intent** | Confirm the tests would notice a defect, not merely execute the line |
| **Depends on** | K1 |

**How**
`mvn -B verify` for coverage; `make mutation` for PIT. Every surviving mutant is either
fixed or excluded **with a written justification** that it is equivalent.

**Avoid**
- Raising thresholds to match the result. The threshold is the commitment; the result is the measurement

| Field | Value |
|---|---|
| **Produces** | Coverage + mutation evidence |
| **Verify** | `mvn -B verify && make mutation` |
| **Pass when** | 90/90 coverage; domain ≥85%, application ≥75% mutation |
| **On fail / Rollback** | Add the missing tests |

### K3 — Acceptance criteria audit

| Field | Value |
|---|---|
| **Type** | verify |
| **Target** | workflow §10 |
| **Intent** | Every claim traced to a command and its output |
| **Depends on** | K1 |

**How**
Walk every AC across all seven workflows in order. For each, record the command run and the observed result.
An AC that is not done is recorded as **not done** — not quietly omitted.

| Field | Value |
|---|---|
| **Produces** | AC status table |
| **Verify** | Every AC has a verdict and evidence |
| **Pass when** | No AC is unaddressed |
| **On fail / Rollback** | — |

### K4 — Re-verify the IAR

| Field | Value |
|---|---|
| **Type** | verify |
| **Target** | workflow §3.0 |
| **Intent** | Upstream may have drifted since the workflow was written |
| **Depends on** | — (entry) |

**How**
Re-run the six `curl | jq` probes for IA1–IA6 in [WF-000](../workflows/WF-000-foundation.md). A changed shape is a real finding and belongs
in the report, not in a surprise during the demo.

| Field | Value |
|---|---|
| **Produces** | Confirmed or corrected IAR |
| **Verify** | Six probes |
| **Pass when** | All CONFIRMED, or the difference is documented and handled |
| **On fail / Rollback** | Update the mapper and its fixture |

### K5 — Execution report

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `docs/execution-reports/pokedex-api-execution-report.md` |
| **Intent** | An honest record of what was built and what was not |
| **Depends on** | K2, K3, K4 |

**How**
Two parts per [WF-999 §11](../workflows/WF-999-delivery.md): narrative (what was done, before/after, feature map, what this
enables, **what is still missing**) then technical detail (result, metrics, files,
deviations, verification, known issues, AC status).

**Avoid**
- An empty "What's Still Missing". A report claiming completeness it does not have destroys the credibility of every other claim in it

| Field | Value |
|---|---|
| **Produces** | The report |
| **Verify** | Read it back against §11 |
| **Pass when** | Every section present; gaps named plainly |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] `make verify` green on a clean clone
- [ ] Coverage and mutation thresholds met
- [ ] Every AC recorded pass or explicitly deferred, with evidence
- [ ] IAR re-verified
- [ ] Execution report written and honest

```bash
git clone . /tmp/verify && cd /tmp/verify && make verify && make mutation
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | all |

## Blocks

none — this is the terminal work unit

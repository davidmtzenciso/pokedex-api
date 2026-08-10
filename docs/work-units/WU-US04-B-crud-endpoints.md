# WU-US04-B — Local CRUD Endpoints

| Field | Value |
|---|---|
| **Work Unit** | WU-US04-B |
| **Parent** | [WF-US04 Local Data Modification](../workflows/WF-US04-local-data-modification.md) |
| **Objective contribution** | The six operations, and the error contract the story grades |
| **Estimate** | M |
| **Status** | not started |

## Objective

Expose CRUD, and make the error responses the story names — 404 and 400 — plus the ones it
implies.

## Entry Criteria

- WU-US04-A green, WU-AUTH-C green (mutations are protected)

## Outputs

- `LocalPokemonController`, complete `GlobalExceptionHandler` coverage, component tests

---

## ▶ Activity Sequence

### B1 — `LocalPokemonController`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `web/…/controller/LocalPokemonController.java` |
| **Intent** | Six operations, thin |
| **Depends on** | — (entry) |

**How**
`implements LocalPokemonApi`. `POST` returns 201 with `Location`. `DELETE` returns 204.
Reads are public; mutations are protected.

| Field | Value |
|---|---|
| **Produces** | The endpoints |
| **Verify** | `mvn -B test` |
| **Pass when** | Slice tests green; `OA1` satisfied |
| **On fail / Rollback** | — |

### B2 — Complete the error contract

| Field | Value |
|---|---|
| **Type** | edit |
| **Target** | `web/…/error/GlobalExceptionHandler.java` + tests |
| **Intent** | The story names 404 and 400 and then asks for "further defensive logic" |
| **Depends on** | B1 |

**How**
One `@ExceptionHandler` per row of [WF-US04 §9.5](../workflows/WF-US04-local-data-modification.md),
each producing a `ProblemDetail` with `code`, `traceId`, `timestamp`. Validation adds
`errors[]` with a `field` per entry.

**Map both parameter-validation exception types** — `ConstraintViolationException` and
`HandlerMethodValidationException` — to an **identical** 400 shape. Framework 7 renamed the
accessor to `getParameterValidationResults()`.

**Avoid**
- Mapping only one of them. Roughly half your 400s then return 500 (IA9)
- A stack trace in the response body

| Field | Value |
|---|---|
| **Produces** | The error contract |
| **Verify** | `mvn -B verify` |
| **Pass when** | Every §9.5 row has a test asserting the exact status **and** `code` |
| **On fail / Rollback** | — |

### B3 — The nine mandatory scenarios

| Field | Value |
|---|---|
| **Type** | test |
| **Target** | `web/…/LocalPokemonComponentTest.java` |
| **Intent** | The CRUD contract, exhaustively |
| **Depends on** | B2 |

**How**
Create 201 · create duplicate 409 · get 200 · get 404 · list 200 · update 200 · update 404 ·
delete 204 · get-after-delete 404. Plus concurrent `PATCH` producing exactly one 412.

| Field | Value |
|---|---|
| **Produces** | Story-level proof |
| **Verify** | `mvn -B verify` |
| **Pass when** | AC-US04-1 … AC-US04-7 green |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] `PUT /local/9999` → **404** `problem+json` (AC-US04-2, named in the story)
- [ ] `mass: -5` → **400** with `errors[]` naming the field (AC-US04-3, named in the story)
- [ ] Concurrent `PATCH` → exactly one 412 (AC-US04-4)
- [ ] `DELETE` removes children; subsequent `GET` is 404 (AC-US04-5)
- [ ] Both validation exception types produce an identical 400

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-US04-1 … AC-US04-7 |
| Decision | [ADR-0003](../adr/0003-rfc9457-problemdetail.md), [ADR-0010](../adr/0010-hard-deletes.md) |

## Blocks

WU-999-A

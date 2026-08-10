# WU-AUTH-A — User Domain

| Field | Value |
|---|---|
| **Work Unit** | WU-AUTH-A |
| **Parent** | [WF-AUTH User Management](../workflows/WF-AUTH-user-management.md) |
| **Objective contribution** | The `User` aggregate, refresh-token families, and the rotation rule |
| **Estimate** | S |
| **Status** | not started |

## Objective

Model the exercise's "secondary collection" and the invariant that makes token theft
containable.

## Entry Criteria

- WU-000-C green (value objects and ports exist)

## Outputs

- `User` aggregate, `RefreshToken` child, `RefreshTokenRotationUseCase`

---

## ▶ Activity Sequence

### A1 — `User` aggregate

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/model/User.java`, `domain/…/vo/{Email,PasswordHash,Username}.java` + tests |
| **Intent** | A password that cannot leak by accident |
| **Depends on** | — (entry) |

**How**
`Email` normalises and lower-cases on construction. `PasswordHash.toString()` returns
`"***"` so it cannot reach a log line through string interpolation. `@JsonIgnore` on any
serialisable path. Roles as a closed enum set.

**Conventions**
- Records, validation in the compact constructor → [java patterns](../handbook/java-patterns.md)

**Avoid**
- A `password` field of any kind on the aggregate. Only the hash exists in the domain

| Field | Value |
|---|---|
| **Produces** | The aggregate |
| **Verify** | `mvn -B test -Dtest=UserTest,UserSerializationTest` |
| **Pass when** | A test asserts the hash appears in **no** serialised form and no log output (I10) |
| **On fail / Rollback** | — |

### A2 — `RefreshToken` and the family rule

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `domain/…/model/RefreshToken.java`, `application/…/usecase/RefreshTokenRotationUseCase.java` + tests |
| **Intent** | Make replay of a rotated token detectable and containable |
| **Depends on** | A1 |

**How**
Each token carries a `familyId` established at login. Rotation revokes the presented token
and issues a successor in the same family. Presenting an **already-revoked** token revokes
the entire family — **synchronously**, because a stolen token must not survive the request
that detected it.

**Patterns**
- Rotation with family revocation → [refresh-token-rotation.md](../diagrams/refresh-token-rotation.md)

**Avoid**
- Making family revocation an async domain event. It is the one event that cannot be deferred

| Field | Value |
|---|---|
| **Produces** | Rotation with theft detection |
| **Verify** | `mvn -B test -Dtest=RefreshTokenRotationTest` |
| **Pass when** | Invariant I8 holds — at most one live token per family (F11); reuse revokes **all** of them |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] The password hash never appears in a response or a log
- [ ] Reuse of a rotated token revokes the whole family, synchronously
- [ ] Mutation score ≥ 85% on the affected domain classes

```bash
mvn -B test
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-AUTH-2, AC-AUTH-4 |
| Decision | [ADR-0005](../adr/0005-es256-jwt-jti-sessions.md) |

## Blocks

WU-AUTH-B

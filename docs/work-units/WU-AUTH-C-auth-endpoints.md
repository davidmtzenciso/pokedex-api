# WU-AUTH-C — Auth Endpoints and Route Policy

| Field | Value |
|---|---|
| **Work Unit** | WU-AUTH-C |
| **Parent** | [WF-AUTH User Management](../workflows/WF-AUTH-user-management.md) |
| **Objective contribution** | `/v1/security/**` and the deny-by-default filter chain |
| **Estimate** | M |
| **Status** | done |

## Objective

The five auth operations, and the route policy every other workflow's mutating endpoints
depend on.

## Entry Criteria

- WU-AUTH-B green, WU-000-B green (generated `SecurityApi`)

## Outputs

- `SecurityController`, `SecurityConfig`, auth use cases, component tests

---

## ▶ Activity Sequence

### C1 — Auth use cases

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `application/…/usecase/{RegisterUser,AuthenticateUser,Logout}UseCase.java` + tests |
| **Intent** | Registration, login, and a logout that actually logs out |
| **Depends on** | — (entry) |

**How**
`Register` rejects a taken username or email with `UserAlreadyExistsException`.
`Authenticate` verifies with BCrypt and issues a token pair plus a `jti` session. `Logout`
deletes the session.

**Avoid**
- Distinguishing "unknown user" from "wrong password" in the response. Both are 401 — otherwise the endpoint is a user-enumeration oracle (`java:S5804`)

| Field | Value |
|---|---|
| **Produces** | Three use cases |
| **Verify** | `mvn -B test` |
| **Pass when** | Unknown user and wrong password produce an identical 401 |
| **On fail / Rollback** | — |

### C2 — `SecurityController`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `web/…/controller/SecurityController.java` |
| **Intent** | Thin delegation, implementing the generated interface |
| **Depends on** | C1 |

**How**
`implements SecurityApi`. Bind, delegate, map. Registration returns 201; login returns the
token pair; logout 204.

| Field | Value |
|---|---|
| **Produces** | The endpoints |
| **Verify** | `mvn -B test` |
| **Pass when** | `@WebMvcTest` slice green; `OA1` satisfied |
| **On fail / Rollback** | — |

### C3 — `SecurityConfig` — deny by default

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `web/…/config/SecurityConfig.java` + component test |
| **Intent** | An omission must fail closed, not open |
| **Depends on** | C2 |

**How**
Filter order: rate limit → JWT verify → session check → route rules. Public routes are an
**enumerated allow-list** from [WF-AUTH §3](../workflows/WF-AUTH-user-management.md). The
chain terminates in `.anyRequest().authenticated()`. CORS is an explicit origin allow-list,
never `*`.

**Conventions**
- `SB-PA4` asserts the terminating `.anyRequest()` → [archunit governance](../guides/archunit-governance.md)

**Avoid**
- A `permitAll()` pattern broader than intended. `/v1/pokedex/**` would open every mutation

| Field | Value |
|---|---|
| **Produces** | The route policy |
| **Verify** | `mvn -B verify -Dtest=AuthEnforcementComponentTest` |
| **Pass when** | Every mutating endpoint returns 401 unauthenticated and **writes no row** |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] Every mutating endpoint 401s unauthenticated, with no side effect
- [ ] Replaying a rotated refresh token revokes the family
- [ ] A 403 means authorisation; a bad signature is 401
- [ ] `SB-PA4` passes

```bash
mvn -B verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-AUTH-1, AC-AUTH-2, AC-AUTH-5 |

## Blocks

WU-US03-C, WU-US04-B

# WF-AUTH — User Management and Route Protection

> **Requirement**: *Implement an auxiliary API for user registration, authentication, and the management of protected versus public routes.* Plus the data requirement for *a secondary collection for user management* with a unique primary key and at least two descriptive attributes.
> **Depends on**: [WF-000](WF-000-foundation.md)
> **Delivers**: `/v1/security/**`, the filter chain, and the public-versus-protected route policy
> **Estimate**: L
> **Work units**: [WU-AUTH-A](../work-units/WU-AUTH-A-user-domain.md) · [WU-AUTH-B](../work-units/WU-AUTH-B-security-adapters.md) · [WU-AUTH-C](../work-units/WU-AUTH-C-auth-endpoints.md)

> Not a numbered user story, but a first-class requirement in its own right — and every
> mutating endpoint in US03 and US04 depends on it.

---

## 1. Summary

Registration, login, and a route policy that denies by default. The two decisions worth
defending are asymmetric ones: tokens are **asymmetrically signed** so verifiers cannot mint
them, and the session store **fails closed** while the cache next to it fails open.

---

## 2. Design decisions specific to this requirement

| # | Decision | Alternatives | Rationale | Consequence |
|---|---|---|---|---|
| 1 | ES256 (asymmetric) | HS256 shared secret | A shared secret lets every verifier mint tokens | Keystore management, `kid` rotation — [ADR-0005](../adr/0005-es256-jwt-jti-sessions.md) |
| 2 | `jti` → Redis session, checked per request | Pure stateless JWT | Otherwise "logout" is a client-side gesture and the token stays valid until expiry | Redis on the auth path, **fail closed** |
| 3 | Refresh rotation with a per-login `familyId` | Long-lived refresh token | Replay of a rotated token is the signature of theft | Reuse revokes the **whole family**, synchronously |
| 4 | Deny by default | Enumerate protected routes | An omission then fails closed rather than open | ArchUnit `SB-PA4` asserts the terminating `.anyRequest()` |
| 5 | BCrypt cost 12 for passwords | SHA-256; Argon2 | SHA-256 is not a password hash; BCrypt is available and sufficient | `java:S5344` satisfied |

---

## 3. Specification

| Verb | Path | Auth | Success | Errors |
|---|---|:---:|---|---|
| POST | `/v1/security/register` | 🔓 | 201 | 400, 409 |
| POST | `/v1/security/login` | 🔓 | 200 + token pair | 400, 401 |
| POST | `/v1/security/token/refresh` | 🔓 | 200 | 401, 409 |
| POST | `/v1/security/logout` | 🔒 | 204 | 401 |
| GET | `/v1/security/me` | 🔒 | 200 | 401 |

### Route policy

| Path | Public? | Why |
|---|:---:|---|
| `/v1/security/{register,login,token/refresh}` | yes | You cannot authenticate in order to authenticate |
| `/v1/security/{logout,me}` | no | Both need a principal |
| `GET /v1/pokedex/pokemon/**`, `GET /v1/pokedex/local/**` | yes | Read-only reference data |
| `POST PUT PATCH DELETE /v1/pokedex/**` | no | Mutations must be attributable |
| `/actuator/health` | yes | Container healthcheck |
| everything else | no | `anyRequest().authenticated()` |

### Token design

| Concern | Choice |
|---|---|
| Algorithm | ES256, allow-listed. `alg: none` rejected |
| Claims | `iss`, `aud` (allow-listed), `sub`, `jti`, `roles`, `exp`, `iat` — **no PII** |
| Access TTL | 15 minutes |
| Refresh TTL | 7 days, rotated on every use |
| Revocation | `jti` in Redis; logout deletes it |

---

## 4. Domain delta

Adds the **`User`** aggregate and its `RefreshToken` child — the exercise's "secondary
collection", with a unique primary key and well beyond two descriptive attributes.

Invariants: **I8** (at most one live token per family), **I10** (a password is never stored
in clear, logged, or returned), **I11** (every mutating endpoint requires a principal).
Formal constraint **F11**.

---

## 5. Flow

[auth-filter-chain.md](../diagrams/auth-filter-chain.md) ·
[refresh-token-rotation.md](../diagrams/refresh-token-rotation.md)

---

## 9.5 Error paths

| Condition | Response | Note |
|---|---|---|
| Missing or malformed bearer token | 401 `UNAUTHENTICATED` | |
| `alg: none` or bad signature | 401 `INVALID_TOKEN` + security audit log | |
| Valid signature, `jti` revoked | 401 `TOKEN_REVOKED` | This is what makes logout real |
| Refresh-token **reuse** | 401 `TOKEN_REUSE_DETECTED`, **entire family revoked synchronously** | Treated as theft |
| Authenticated but lacking the role | **403** `FORBIDDEN` | A 403 means authorisation, never a bad signature |
| Username or email taken | 409 `USER_ALREADY_EXISTS` | |
| Redis unreachable on a session check | 401 — **fail closed** | The deliberate opposite of the cache |

---

## 10. Acceptance criteria

**AC-AUTH-1**: Given an unauthenticated request to any mutating endpoint, then 401 returns
and **no row is written**.

**AC-AUTH-2**: Given a rotated refresh token, when the **old** one is replayed, then 401
returns, the entire family is revoked, and a security event is logged.

**AC-AUTH-3**: Given a token signed with `alg: none` or an unknown `kid`, then 401 returns
and the failure is logged.

**AC-AUTH-4**: Given any API response or log line, then no password, password hash, or
keystore secret appears anywhere.

**AC-AUTH-5**: Given logout, when the same access token is replayed, then 401
`TOKEN_REVOKED` — the token is otherwise still within its `exp`.

**AC-AUTH-6**: Given Redis is unreachable, when a protected endpoint is called, then **401**
— never 200.

**AC-AUTH-7**: Given `gitleaks detect`, then zero secrets are found and the dev keystore is
confirmed gitignored.

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R14 | The fail-open cache handler is copied into the session store | Med | High | R | Named explicitly in [WU-AUTH-B](../work-units/WU-AUTH-B-security-adapters.md) `Avoid`; a component test with Redis stopped asserts 401 |
| R11b | A reviewer reads ES256 + JWKS as over-engineering | Low | Low | G | [ADR-0005](../adr/0005-es256-jwt-jti-sessions.md) states the trade in two sentences; be ready to say "HS256 would also pass — here is why I chose otherwise" |
| R15 | The dev keystore is committed | Low | High | Y | `keys/` gitignored in WU-000-A; `gitleaks` before every commit |

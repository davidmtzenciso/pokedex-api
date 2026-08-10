# WU-AUTH-B — Security Adapters

| Field | Value |
|---|---|
| **Work Unit** | WU-AUTH-B |
| **Parent** | [WF-AUTH User Management](../workflows/WF-AUTH-user-management.md) |
| **Objective contribution** | ES256 tokens, BCrypt, and a session store that fails closed |
| **Estimate** | M |
| **Status** | not started |

## Objective

Implement `TokenIssuer`, `PasswordHasher`, and the `jti` session store — with the fail-closed
decision made deliberately rather than copied from the cache.

## Entry Criteria

- WU-AUTH-A green

## Outputs

- `Es256TokenIssuer`, `BCryptPasswordHasher`, `RedisSessionStore`

---

## ▶ Activity Sequence

### B1 — `Es256TokenIssuer` and `BCryptPasswordHasher`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `infrastructure/…/security/` + tests |
| **Intent** | Verifiers hold only the public key and cannot mint tokens |
| **Depends on** | — (entry) |

**How**
`jjwt` 0.13. ES256 from a PKCS12 keystore; `kid` in the header. Claims `iss`, `aud`
(allow-listed), `sub`, `jti`, `roles`, `exp`, `iat`. **No PII.** Verification allow-lists
ES256 and rejects `alg: none`. BCrypt cost 12.

**Conventions**
- Keystore password from an environment variable, never a properties file (`java:S6437`)

**Avoid**
- HS256 with a shared secret — every verifier could then mint tokens
- SHA-256 for passwords (`java:S5344`)

| Field | Value |
|---|---|
| **Produces** | Token issuer and password hasher |
| **Verify** | `mvn -B test -Dtest=Es256TokenIssuerTest` |
| **Pass when** | `alg: none` and a wrong `aud` are **rejected**; claims asserted explicitly, never `!= null` |
| **On fail / Rollback** | — |

### B2 — `RedisSessionStore` — fail **closed**

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `infrastructure/…/security/RedisSessionStore.java` + component test |
| **Intent** | This is what makes logout real |
| **Depends on** | B1 |

**How**
`jti` → Redis with the access-token TTL. Every verified request checks liveness; logout
deletes the key. On `RedisConnectionFailureException`: log ERROR and return **false**, so the
request gets 401.

**Conventions**
- Session reads fail **closed** → [auth filter chain](../diagrams/auth-filter-chain.md)

**Avoid**
- Copying the fail-**open** handler from the cache adapter ([WU-US01-B](WU-US01-B-cache.md)). Same dependency, opposite policy — and getting it backwards silently grants access. This is risk R14

| Field | Value |
|---|---|
| **Produces** | Session store |
| **Verify** | Component test with Redis stopped |
| **Pass when** | Protected requests return **401**, not 200 (AC-AUTH-6) |
| **On fail / Rollback** | — |

---

## Exit Criteria

- [ ] `alg: none` and wrong-`aud` rejected, with tests
- [ ] Logout invalidates a token that is otherwise still within `exp`
- [ ] Redis down ⇒ 401, never 200
- [ ] `keys/` gitignored; `gitleaks detect` clean

```bash
mvn -B verify && gitleaks detect
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-AUTH-3, AC-AUTH-5, AC-AUTH-6, AC-AUTH-7 |
| Decision | [ADR-0005](../adr/0005-es256-jwt-jti-sessions.md) |

## Blocks

WU-AUTH-C

# ADR-0005: ES256 JWT with Redis `jti` Sessions and Refresh-Token Rotation

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

The exercise requires "an auxiliary API for user registration, authentication, and the management of protected versus public routes." The default reflex is HS256 with a shared secret in configuration — it works, it is three lines of setup, and it would pass.

It also has two properties worth avoiding. A shared symmetric secret means every verifier can also *mint* tokens, and a pure stateless JWT cannot be revoked: "log out" becomes a client-side gesture while the token remains valid until it expires.

The platform's `jwt-jose.md` guidance is explicit — "Prefer asymmetric (ES256/RS256) so verifiers can't mint tokens" and "Don't use: HS256 shared secret across services."

## Decision

- **Algorithm**: ES256 (ECDSA P-256). Signing key from a local PKCS12 keystore; `kid` identifies the key. Algorithm allow-listing on verify — `alg: none` and any algorithm other than ES256 are rejected outright, which closes both the `none` attack and RS256→HS256 key confusion.
- **Claims**: `iss` from configuration, `aud` validated against an allow-list, `sub` = user id, `jti` = random UUID, plus `roles`. **No PII in claims** — signed is not encrypted.
- **Sessions**: `jti` is written to Redis with the access-token TTL. Every verified request checks that the `jti` session is still live. Logout deletes it, so revocation is immediate and real.
- **Refresh**: rotation with a per-login `familyId`. Presenting a refresh token issues a new one and revokes the old. Presenting an already-rotated token is treated as theft: the **entire family is revoked synchronously** and a security event is logged (invariant I8, formal constraint F11).
- **Routes**: deny by default. The filter chain terminates in `anyRequest().authenticated()`, asserted by ArchUnit `SB-PA4`. Public routes are an explicit, enumerated allow-list: registration, login, refresh, and the read-only browse and detail endpoints.

Passwords are hashed with BCrypt. SHA-256 is used only for non-password digests (`java:S5344`).

## Alternatives Considered

1. **HS256 with a shared secret** — Simplest possible setup and adequate for a single-service demo. Rejected because it gives every verifier minting power and because it invites the secret into `application.properties`, which is a `java:S6437` violation waiting to happen. The cost of ES256 here is one keystore and about twenty lines.
2. **Pure stateless JWT, no session store** — The textbook JWT position: no server state, perfect horizontal scaling. Rejected because logout would not actually log anyone out, and AC4b (replayed refresh token revokes the family) is unimplementable without server-side state. Redis is already in the stack for the upstream cache, so the marginal cost is zero.
3. **Opaque tokens with an introspection endpoint** — Strongest revocation story. Rejected as a round trip per request for a single-service deployment with no third-party resource servers.
4. **Session cookies, no JWT at all** — Perfectly defensible for a same-origin SPA and arguably simpler. Rejected because the exercise's "protected versus public routes" framing and the SPA/API split make bearer tokens the more conventional demonstration, and because refresh rotation is a better story to walk a panel through.

## Consequences

### Positive
- Verifiers hold only the public key; compromise of a verifier cannot forge tokens.
- Logout genuinely invalidates. Refresh-token theft is detected and contained rather than silently exploited.
- Key rotation is a `kid` change, not a redeploy of every consumer.
- Satisfies OWASP A01 (deny by default), A02, and A07 without bespoke work.

### Negative
- A keystore must exist before the app boots — one more setup step, and one more thing that can be misconfigured in a demo. Mitigated by a `make keys` target and a throwaway dev keystore.
- Verification now touches Redis on every request. This is a **fail-closed** dependency: if Redis is down, requests 401 rather than being silently granted. That is the correct trade, and it is the opposite of the cache's fail-open behaviour — the asymmetry is deliberate.
- Slightly more moving parts to explain in review than `jwt.secret=changeme`.

### Neutral
- Requires `jjwt` 0.13.x rather than hand-parsing, which is mandated anyway.
- The dev keystore password lives in an environment variable and never in a committed properties file.

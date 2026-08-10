# Logging

Logs exist for one purpose: reconstructing what happened during an incident you were not
watching. Everything below follows from that.

---

## The domain does not log

`domain` has no logger on its classpath, because it has no dependencies at all. That is not
an inconvenience to work around — it is correct. A pure function has nothing to report: it
takes inputs, returns a value or throws, and the caller knows everything that happened.

If you find yourself wanting a logger in `domain`, you are usually about to log something the
caller can see anyway.

| Layer | Logs? | What |
|---|:---:|---|
| `domain` | **no** | Nothing. No logger on the classpath |
| `application` | sparingly | Use-case outcomes worth an audit trail |
| `infrastructure` | yes | Every boundary crossing — HTTP, database, cache |
| `web` | yes | One line per request; the exception handler |

---

## Levels

| Level | Use for | Example |
|---|---|---|
| `ERROR` | Something is broken and a human should look | Session store unreachable; unmapped exception |
| `WARN` | Degraded but handled | Cache unreachable, falling through to upstream; upstream retry exhausted |
| `INFO` | Business events worth an audit trail | Sync completed, user registered, token family revoked |
| `DEBUG` | Diagnostics for a developer reproducing a problem | Cache hit/miss, upstream call durations |
| `TRACE` | Almost never | Payload dumps in local development only |

> **A handled failure is `WARN`, not `ERROR`.** A Redis cache outage that we degrade around
> is working as designed. If every degradation logs `ERROR`, `ERROR` stops meaning
> "something is broken" and nobody reads it.

---

## Never log

| Never | Why |
|---|---|
| Passwords, password hashes | Obvious, and `PasswordHash.toString()` returns `"***"` for exactly this reason |
| JWTs, refresh tokens, `jti` values in full | A log aggregator becomes a credential store. Log the **last 6 chars** if you need correlation |
| Keystore paths with secrets, environment secrets | `gitleaks` catches the commit; nothing catches the log line |
| Email addresses or any PII | OWASP A09 |
| Full request or response bodies | They contain all of the above |

```java
log.info("login succeeded for user {}", user.id());              // yes — an id
log.info("login succeeded for {}", user.email());                // no — PII
log.debug("token issued: {}", token);                            // no — a credential
log.debug("token issued, jti suffix {}", jti.substring(jti.length() - 6));  // acceptable
```

---

## Mechanics

```java
private static final Logger log = LoggerFactory.getLogger(SyncPokemonUseCase.class);
```

- **Parameterised, never concatenated** (`java:S2629`). `log.debug("x {}", expensive())` still evaluates `expensive()`; guard with `isDebugEnabled()` when the argument is costly.
- **Logger named for the enclosing class** (`java:S3416`).
- **Never `System.out`** (`java:S106`).
- **Log *or* rethrow, never both** (`java:S2139`). Double logging doubles the noise in an incident and makes one failure look like two.

---

## Correlation — the `traceId`

Every request gets a trace id in the MDC, and the **same id appears in the RFC 9457 error
body**:

```json
{ "code": "UPSTREAM_UNAVAILABLE", "traceId": "0af7651916cd43dd8448eb211c80319c" }
```

That is the whole point. A user reports a failure, quotes the `traceId`, and you find every
log line for that request. Without it you are grepping timestamps.

A filter populates the MDC at the start of the request and **clears it in a `finally`** —
with virtual threads a leaked MDC entry leaks per task, which is a worse memory profile than
the platform-thread equivalent.

---

## The volume trap this design creates

A cold page issues **1 + 2N** upstream calls — 21 at the default page size, 201 at the
maximum. Logging each call at `INFO` produces 21 lines for one request, and 201 for a large
page. Do that and the log becomes unreadable exactly when you need it.

```java
// no — 2N lines per request
refs.forEach(ref -> { log.info("fetching {}", ref.name()); fetchOne(ref); });

// yes — one line, with the numbers that matter
log.info("page fetch complete: size={} upstreamCalls={} cacheHits={} durationMs={}",
         refs.size(), calls, hits, elapsed.toMillis());
```

**Log the fan-out as a summary, not per call.** Per-call detail belongs at `DEBUG`, where it
is off by default and available when you are actually debugging.

The same reasoning applies to batch sync: one summary line with succeeded, failed, and
skipped counts, plus the failed ids — not 200 lines.

---

## What to log at each boundary

| Boundary | Level | Content |
|---|---|---|
| Inbound request | `INFO` | method, path, status, duration, `traceId`. **One line, on completion** |
| Upstream call | `DEBUG` per call, `INFO` summary | resource, status, duration; retries and circuit-breaker transitions at `WARN` |
| Cache | `DEBUG` | hit or miss and the key. A cache **failure** is `WARN` |
| Database | — | Let Hibernate statistics handle it in dev; do not hand-log queries |
| Security events | `INFO` or `WARN`, **always** | Login success, logout, token-reuse detection, `alg: none` rejection. These are the audit trail |
| Exception handler | matches severity | 4xx at `DEBUG` — client errors are not our incidents. 5xx at `ERROR` with the stack trace **in the log, never in the response** |

> **4xx at `DEBUG` is deliberate.** A stream of 404s is a client with a bad link, not an
> outage. Logging them at `WARN` trains everyone to ignore `WARN`.

---

## Security events are tested, not hoped for

Some log lines are requirements. `AC-AUTH-2` says a replayed refresh token revokes the family
**and logs a security event** — so the test asserts the log line, using a Logback list
appender:

```java
assertThat(logCaptor.getWarnLogs())
    .anyMatch(l -> l.contains("token reuse detected") && l.contains("familyId"));
```

If a log line is part of an acceptance criterion, assert it. Otherwise it is cheap talk and
will be deleted by someone tidying up.

---

## Configuration

| Profile | Format | Level |
|---|---|---|
| `dev` | Human-readable pattern | `INFO` root, `DEBUG` for `com.elatusdev.pokedex` |
| `test` | Pattern, minimal | `WARN` root |
| `prod` | **Structured JSON** | `INFO` root, `WARN` for third-party noise |

Structured JSON in production because a log aggregator can query fields but not prose.
Human-readable in development because you are reading it with your eyes.

---

## Related

[Error handling](error-handling.md) · [Concurrency](concurrency.md) · [Spring patterns](spring-patterns.md)

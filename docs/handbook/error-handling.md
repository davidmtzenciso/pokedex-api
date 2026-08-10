# Error Handling

One throw per method, a specific domain exception, translated in exactly one place.

---

## The shape

```
domain method → throws a typed domain exception
      ↓
use case → does not catch it; lets it propagate
      ↓
the context's @RestControllerAdvice → maps it to a ProblemDetail
      ↓
client → application/problem+json with a stable `code`
```

The use case never sees an HTTP status. If you find yourself importing `HttpStatus` into
`application`, ArchUnit `L4` stops you — and it is right to.

---

## Domain exceptions

```java
public class PokemonNotFoundException extends RuntimeException {
    private final transient PokemonId id;

    public PokemonNotFoundException(PokemonId id) {
        super("No Pokemon with id " + id.value());
        this.id = id;
    }

    public PokemonId id() { return id; }
}
```

- Extends `RuntimeException`, lives in `domain.exception`.
- **Carries context as a field**, not only in the message — the handler needs it to build a useful `detail`.
- `transient` on the field because the exception may be serialised in a log pipeline (`java:S1165` prefers final fields).
- One exception type per failure mode. `PokemonNotFoundException` and `UserNotFoundException` are different types, because they map to different `code` values.

## The translation layer

```java
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(PokemonNotFoundException.class)
    ProblemDetail handle(PokemonNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Pokemon not found",
                       ex.getMessage(), "POKEMON_NOT_FOUND", request);
    }

    // BOTH parameter-validation exception types map to the same 400 shape
    @ExceptionHandler({ ConstraintViolationException.class, HandlerMethodValidationException.class })
    ProblemDetail handleValidation(Exception ex, HttpServletRequest request) { … }
}
```

This is the **only** place an exception becomes a status code. One place to read, one place
to change, one place to test.

## The wire format — RFC 9457

```json
{
  "type": "https://pokedex.elatus-dev.com/problems/pokemon-not-found",
  "title": "Pokemon not found",
  "status": 404,
  "detail": "No Pokemon with id 9999 exists",
  "instance": "/api/v1/pokedex/local/9999",
  "code": "POKEMON_NOT_FOUND",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "timestamp": "2026-08-09T14:22:31Z",
  "errors": [{ "field": "region", "message": "must be one of KANTO, JOHTO, …" }]
}
```

`code` is the contract. Clients branch on it, never on `detail` — prose changes, codes do
not. `errors[]` appears only on 400.

## The mapping table

| Exception | Status | `code` |
|---|---|---|
| `PokemonNotFoundException` | 404 | `POKEMON_NOT_FOUND` |
| `DuplicatePokemonException` | 409 | `DUPLICATE_POKEMON` |
| `IllegalStateTransitionException` | 409 | `ILLEGAL_STATE_TRANSITION` |
| `OptimisticLockingFailureException` | 412 | `STALE_VERSION` |
| `InvalidPokemonDataException` | 400 | `VALIDATION_ERROR` |
| `ConstraintViolationException` **and** `HandlerMethodValidationException` | 400 | `VALIDATION_ERROR` |
| `UserAlreadyExistsException` | 409 | `USER_ALREADY_EXISTS` |
| `UpstreamUnavailableException` | 502 | `UPSTREAM_UNAVAILABLE` |
| `UpstreamTimeoutException` | 504 | `UPSTREAM_TIMEOUT` |
| `CircuitOpenException` | 503 + `Retry-After` | `UPSTREAM_CIRCUIT_OPEN` |
| anything unmapped | 500 | `INTERNAL_ERROR` — logged with the trace id, **never** the stack trace in the body |

> **502 and 504 exist deliberately.** An upstream outage is not a 500 on *our* service. This
> is the "further defensive logic as required" the brief asks for, and it is the difference
> between "our API broke" and "PokeAPI is down and we degraded gracefully".

## Rules

| Rule | Why |
|---|---|
| **One `throw` per method** | Multiple exit conditions usually means the method does two things |
| **Never catch `Exception` or `Throwable`** | You will swallow `InterruptedException` and programming errors alike (`B-05`, `java:S1181`) |
| **No empty catch blocks** | Log, rethrow, or wrap. Silence is the worst option (`B-06`) |
| **Log *or* rethrow, never both** | Double logging doubles the noise in an incident (`java:S2139`) |
| **Never expose a stack trace in a response** | Information disclosure, and useless to the caller |
| **Never use exceptions for control flow** | An expected empty result is `Optional.empty()`, not an exception |
| **Restore the interrupt** | `catch (InterruptedException e) { Thread.currentThread().interrupt(); … }` (`java:S2142`) |

## Fail open or fail closed — decide deliberately

```java
// cache read: FAIL OPEN — a cache is an optimisation
try { return cache.get(key); }
catch (RedisConnectionFailureException e) {
    log.warn("cache unavailable, falling through to upstream");
    return Optional.empty();
}

// session read: FAIL CLOSED — a session store is a security control
try { return sessionStore.isLive(jti); }
catch (RedisConnectionFailureException e) {
    log.error("session store unavailable, denying request");
    return false;   // 401
}
```

Same dependency, opposite policies. Getting this backwards means either a cache outage takes
the service down, or a Redis outage silently grants access. Never make the choice by accident.

## Related

[Spring patterns](spring-patterns.md) · [Java patterns](java-patterns.md) · [Re-sync error paths](../diagrams/error-paths-resync.md)

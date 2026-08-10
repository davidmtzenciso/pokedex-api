# ADR-0003: RFC 9457 ProblemDetail Instead of the House ApiError Envelope

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

The platform's established error contract is a typed domain exception caught by a single `@RestControllerAdvice` and rendered as an i18n `ApiError` — `ApiError.of("DUPLICATE_ENTITY", messages.get(ex.key(), locale))` — with the canonical envelope `{timestamp, message, path, traceId, code, details[]}`.

This project has a different audience. It is reviewed by an external panel with no exposure to our platform, and the exercise calls out error handling explicitly: "provide 404 responses for missing records, 400 status codes for malformed payloads, and incorporate further defensive logic as required."

The i18n half of `ApiError` also carries real cost here: message bundles, a locale resolution strategy, and a key catalogue, none of which the exercise asks for and none of which a single-locale demo exercises.

## Decision

Use RFC 9457 (Problem Details for HTTP APIs) as the error envelope, served as `application/problem+json`, via Spring Boot 4's native `ProblemDetail` support.

Retain the house pattern's substance:
- One `@RestControllerAdvice` **per context** is the only place exceptions become responses — `AuthExceptionHandler`, `CatalogExceptionHandler`, and the context-free `ValidationExceptionHandler`. A single global advice would have to import every context's exceptions, which makes `shared` depend on all of them (`BC3`).
- Typed domain exceptions, one `throw` per method, translated at the boundary.
- A stable machine-readable `code` survives as an RFC 9457 extension member, so clients still branch on `POKEMON_NOT_FOUND` rather than parsing prose.

Extension members: `code`, `traceId`, `timestamp`, and `errors[]` (present only on 400).

## Alternatives Considered

1. **House `ApiError` + i18n bundles, unchanged** — Maximum consistency with the platform, zero divergence to defend. Rejected because it imports message-bundle machinery for a single-locale application, and because an external reviewer must learn a bespoke envelope before they can assess whether the error handling is any good.
2. **`ProblemDetail` with i18n message resolution inside it** — Genuinely the best of both, and the right answer if this service were joining the platform. Rejected here purely on proportionality: the locale infrastructure has no consumer in this exercise.
3. **Plain `{error, message}` ad-hoc JSON** — Rejected outright. It is what most submissions do and it forfeits the "proper error handling" criterion.

## Consequences

### Positive
- An IETF standard needs no explanation to an outside reviewer; `application/problem+json` is self-describing.
- Native framework support means no custom serialiser and no envelope-wrapping filter.
- The `type` URI gives each failure mode a documentation anchor.
- Validation errors carry per-field detail in `errors[]`, which the SPA renders inline without string parsing.

### Negative
- **Diverges from the platform contract.** A future migration of this service into the platform would need either a translation layer or an envelope swap.
- Client code written against `ApiError` would not work here unmodified.

### Neutral
- Both `ConstraintViolationException` and `HandlerMethodValidationException` must be mapped to an identical shape (IA9) — this is required under either envelope, so it is not a cost of the divergence.
- Supersedes nothing. If the platform later adopts RFC 9457 wholesale, this ADR becomes the precedent rather than the exception.

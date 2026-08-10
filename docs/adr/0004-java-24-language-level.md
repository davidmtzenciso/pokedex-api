# ADR-0004: Java Language Level 24 on a Temurin 25 Runtime

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

Java 25 is the current LTS, released September 2025 and supported on Temurin to 2031. Java 24 was a non-LTS release that reached end of life six months after its March 2025 launch. The instinct is therefore to target 25.

The platform estate, however, standardises on `maven.compiler.release=24` with `eclipse-temurin:25` as the build and run image — language level 24, runtime 25. Spring Boot 4.x requires Java 17 minimum and is tested through Java 26, so both are supported.

## Decision

Set `maven.compiler.release=24`. Build and run on Temurin 25 (verified: 25.0.4+7 LTS).

This is not a compromise between the two positions; it is what the platform already does. The runtime is the current LTS — we get its GC and JIT improvements and its support window. The *language level* is held one release back so that bytecode stays compatible across the estate.

A direct consequence: **structured concurrency (JEP 505) remains a preview feature and is therefore banned.** The concurrent fan-out in the PokeAPI adapter uses virtual threads with `CompletableFuture` and a bounding `Semaphore` instead. Virtual threads themselves have been stable since Java 21 and are fully available.

## Alternatives Considered

1. **Language level 25** — Unlocks nothing this project needs. Structured concurrency is still preview in 25 (fifth preview), so the one feature that would change the fan-out design is unavailable either way. Rejected because it would fragment bytecode compatibility across the estate for zero functional gain.
2. **Language level 21** — The previous LTS, maximally conservative. Rejected: it gives up pattern matching for `switch` refinements and several `java.util` conveniences that the domain model uses, and the platform has already moved past it.
3. **Java 24 runtime as well as language level** — Rejected outright. Java 24 is end-of-life and would ship a container with an unpatched JVM.

## Consequences

### Positive
- Consistent with every other service in the estate; bytecode is portable across them.
- Current-LTS runtime means a supported JVM with modern GC behaviour and virtual-thread refinements.
- Defensible in review with one sentence: production pins to LTS for the runtime and holds the language level at the estate baseline.

### Negative
- No structured concurrency. Concurrent fan-out is more verbose — explicit `CompletableFuture` composition plus a `Semaphore` rather than a `StructuredTaskScope`.
- Sonar rules `java:S8465` and `java:S8432` (ScopedValue) remain active and must be satisfied.

### Neutral
- Docker base image must be digest-pinned (`docker:S6596`, `docker:S8431`), which is required regardless of version choice.
- If the estate moves to language level 25, this ADR is superseded rather than edited.

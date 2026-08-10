# ADR-0002: Contract-First OpenAPI with Generated Interfaces and DTOs

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

An API shape is written down at least twice: once by the service that serves it, once by every client that calls it. Anything hand-written on either side drifts — a renamed field, an added enum value, a nullability change that a consumer discovers at runtime, in front of an audience.

This repository ships no client ([ADR-0009](0009-no-bundled-client.md)), which sharpens the problem rather than removing it. Consumers are not in this build and will not go red when we change a field; they fail later, in someone else's environment. The only thing that can prevent that is a contract both sides derive from mechanically.

The platform standard is unambiguous: `B-20` (CRITICAL) forbids hand-written API DTOs.

## Decision

The OpenAPI 3.1 document at `src/main/resources/openapi/pokedex-api.yaml` is **the source of truth**. It is authored by hand, first, before any controller exists.

- Backend: `openapi-generator-maven-plugin` bound to `generate-sources`, `generatorName=spring`, `interfaceOnly=true`, `useTags=false`, `modelNameSuffix=DTO`. Controllers `implement` the generated `*Api` interfaces.
- Consumers: generate their own types from the published contract, which is served byte-identical to the authored file — see [ADR-0008](0008-openapi-contract-distribution.md).
- ArchUnit rule `OA1` asserts that every `@RestController` implements a generated `*Api`. A hand-rolled endpoint fails the build.

The [work-unit DAG](../diagrams/work-unit-dag.md) places WU-000-B — the spec and the generator wiring — ahead of every work unit that produces a controller. Contract-first is therefore a topological property of the build, not an aspiration.

## Alternatives Considered

1. **Code-first with springdoc annotations** — Faster to start, and the spec stays automatically in sync with the code. Rejected because it inverts the authority: the spec becomes a *report* about the implementation rather than a *contract* the implementation must satisfy. It also produces a spec only after the service compiles, which serialises every consumer behind this build instead of letting all of them start from the same artifact.
2. **Hand-written DTOs on both sides with a shared JSON schema for validation** — Rejected as the worst of both: still two hand-maintained shapes, plus a third artifact to keep aligned.
3. **Publishing a generated client library per consumer language** — Rejected: it makes this repository responsible for other people's build tooling and release cadence. Publishing the document and letting each consumer generate what it wants keeps the coupling at the contract, which is the only place we want it.

## Consequences

### Positive
- This service and its consumers cannot disagree about the wire format; all derive from one file.
- The spec is reviewable on its own, before any code, which is the cheapest point to catch a bad API design.
- Swagger UI is accurate by construction rather than by annotation hygiene.
- Consumer work parallelises with this build from the moment the contract is published — consumers are downstream of the contract, not of the service.

### Negative
- Every endpoint change starts with a YAML edit and a regeneration. This feels slow for the first three endpoints and pays back from roughly the fifth onward.
- Generator configuration is load-bearing and must be correct before any controller exists — hence its position on the critical path and risk R3 in the workflow.
- Generated sources must be excluded from coverage and the source-hygiene gate.

### Neutral
- `useTags=false` means endpoints group by first path segment, not by spec `tags:`. Re-tagging a path has no effect — a known and documented gotcha.
- A schema is defined in exactly one place and `$ref`'d everywhere else; duplicating a shape produces two divergent generated classes.

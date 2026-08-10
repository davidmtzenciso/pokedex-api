# ADR-0008: Publishing the OpenAPI Contract as a Versioned Artifact

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

[ADR-0002](0002-contract-first-openapi.md) made the OpenAPI document the source of truth for this service's HTTP surface. That decision is easy to honour internally — the generator fails the build if the spec and the code disagree.

It says nothing about **consumers**. Any client of this API — a browser SPA, another service, an integration test suite, a partner — derives its own types and expectations from our contract. Those consumers are not in this repository, do not build with this repository, and will not fail when we change a field. They fail later, at runtime, in someone else's environment.

The problem is not that a consumer might be out of date. It is that nothing would tell anyone a consumer is out of date.

## Decision

Treat the OpenAPI document as a **published, versioned artifact** with a stability contract, not as an internal implementation file.

**Publication**

- The authored spec at `src/main/resources/openapi/pokedex-api.yaml` remains the source of truth.
- The running service serves it verbatim at `GET /api/v3/api-docs.yaml`. **AC1c** asserts the served document is byte-identical to the authored one, so a running instance can never advertise a different contract from the one we shipped.
- The spec is **committed to the repository**, so a git tag is a contract version. A consumer pins a tag or commit and fetches the file from it — no registry, no release pipeline, no hosted artifact store. This project builds and runs locally; the distribution mechanism has to work there too.

**Stability**

- `make contract-check` runs `openapi-spec-validator` for validity and `oasdiff` against the spec at the last tag. A breaking change to an existing operation fails the check unless the tag is a new major version (**AC12b**, hard rules H22/H23). It is part of `make verify`, so it runs before every commit rather than after.
- Additive change is always allowed. Breaking change requires a new versioned path or a major release.
- Deprecation is the preferred path: add the new shape, mark the old one `deprecated: true`, remove it a major version later.

**What counts as breaking** is enumerated in [`../guides/contract-consumers.md`](../guides/contract-consumers.md) — including the two that surprise people, making a response field nullable and tightening a validation constraint.

This is deliberately a **one-way** obligation. We publish and we guarantee stability. We do not track, coordinate with, or build against any particular consumer. That asymmetry is the point of [ADR-0009](0009-no-bundled-client.md).

## Alternatives Considered

1. **Serve the spec at runtime only, publish nothing** — Zero machinery, always current. Rejected because a consumer then has nothing to pin. Its build either depends on a running instance of this service, which destroys build reproducibility, or it holds a hand-copied file with no version and no drift signal.
2. **Generate the spec from code annotations at release time** — Removes the byte-identity concern by construction. Rejected in [ADR-0002](0002-contract-first-openapi.md): it inverts the authority, making the contract a report about the implementation rather than an obligation the implementation must meet.
3. **Publish a generated client library** (npm, Maven) — Strongest consumer ergonomics. Rejected: it requires a registry and a publish step per target language, neither of which exists in a local-only build, and it presumes we know what shape of client a consumer wants. Committing the contract lets each consumer generate what suits it.
4. **Semantic versioning of the spec independent of the service version** — More precise, and correct at scale. Rejected as over-specification for a service with one released version so far; the contract version tracks the release version until that becomes limiting.

## Consequences

### Positive
- A consumer can pin an exact contract version and build reproducibly, offline, against it.
- Backward compatibility is a build gate rather than a good intention.
- The byte-identity assertion means a deployed instance cannot silently advertise a contract we never published.
- Breaking changes become explicit, versioned events instead of accidents.

### Negative
- `oasdiff` will occasionally block an intentional break until it is versioned properly. That friction is the feature, but it will still feel obstructive at the time.
- Because the check is local, it is only as reliable as the discipline to run `make verify`. A hosted pipeline would make it unskippable; nothing here does. That is a real weakness of a local-only build and it should be stated rather than glossed over.
- Deprecate-then-remove means carrying two shapes for a release cycle.
- We inherit a stability obligation to consumers we cannot see, which constrains refactoring of the HTTP surface more than of anything behind it.

### Neutral
- The contract version tracks the service release version pre-1.0.
- Consumers are responsible for their own drift detection; we provide the pinnable artifact that makes it possible.

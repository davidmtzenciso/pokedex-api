# Glossary — pokedex-api in plain language

So a term in these docs never sends you on a detour.

---

## Domain

**Replicated fields** — The subset of a `Pokemon` whose authority is PokeAPI: name,
category, mass, height, sprites, description, abilities, stats, types, evolution links,
and upstream-sourced localised names. Overwritten on every re-sync.

**Proprietary fields** — The subset whose authority is the curator: region, notes, tags,
curator-authored localised names, and `curatedBy`. **Never** overwritten by a re-sync.

**The partition** — The fact that `Proprietary ∩ Replicated = ∅`. Because every field has
exactly one authoritative source, re-sync needs no conflict resolution. This is the single
most important idea in the domain model. See [ADR-0007](../adr/0007-proprietary-field-merge-policy.md).

**Merge policy** — `PokemonMergePolicy`, a pure domain class implementing constraint F7:
replicated fields come from upstream, proprietary fields come from the existing record.

**Replication state** — Where a record sits in its lifecycle: `DRAFT`, `PENDING`, `SYNCED`,
`CUSTOMIZED`, `STALE`, `FAILED`. Transitions are constrained by the state machine in
[replication-state-machine.md](../diagrams/replication-state-machine.md); illegal transitions
are 409. `DELETE` is terminal and removes the row — there is no archived state.

**Stale** — `syncedAt + TTL < now`. A derived predicate, never a stored flag.

**Curator** — An authenticated user with the `CURATOR` role. The only actor who can sync
or mutate local records.

**The 1 + 2N problem** — PokeAPI's list endpoint returns only `{name, url}`, so a page of N
rows needs 1 list call + N detail calls + N species calls. 21 at the default page size of
10; 201 at the maximum of 100. The reason caching, the bounded fan-out, and the page-size
cap all exist.

**Page size** — Defaults to 10, capped at 100. A request above the cap is rejected with 400,
not silently clamped — clamping would let a client believe it had received everything.

## Architecture

**Port** — An interface owned by `domain` describing something the domain needs from the
outside world (`PokemonRepository`, `CachePort`, `ClockPort`). Implemented in
`infrastructure`. The dependency arrow points inward.

**Adapter** — The infrastructure class implementing a port. Named `*Adapter`.

**Aggregate root** — The single entry point to a cluster of entities that change together.
`Pokemon` and `User` are the two roots here. External references are id-only.

**Use case** — One class per operation, in `application.usecase`, carrying
`@Transactional`. The transaction boundary. Command/query separation at class level.

**`*DataModel`** — A JPA entity. Lives only in `infrastructure.persistence.model` (`N4`).
Never leaves the infrastructure layer.

**`*DTO`** — A generated API model. Never hand-written (`B-20`). Suffix comes from
`modelNameSuffix=DTO`.

**Contract-first** — The OpenAPI YAML is authored first and generates the Java interfaces
and the TypeScript types. See [openapi-contract-first.md](../guides/openapi-contract-first.md).

## Build and quality

**Layer package** — `domain`, `application`, `infrastructure`, or `web` under
`com.elatusdev.pokedex`. One Maven module; the boundaries are ArchUnit rules, not module
edges — see [ADR-0001](../adr/0001-clean-architecture-layered-packages.md).

**Component test** — `*ComponentTest.java`. Full Spring context against real Postgres and
Redis via Testcontainers. Run by Failsafe under `mvn verify`. Needs Docker.

**ArchUnit** — JUnit tests asserting on compiled bytecode. Enforce layering, naming, and
containment rules the POM cannot express. See [archunit-governance.md](../guides/archunit-governance.md).

**New code** — Lines added or changed. The coverage gate (90% line, 90% branch) applies to
the whole merged report, enforced by JaCoCo in the build.

**Suppression ladder** — `sonar.exclusions` forbidden, `// NOSONAR` forbidden,
`@SuppressWarnings("java:SNNNN")` with a WHY comment acceptable at narrowest scope.

**Cheap talk** — A claim in a doc that nothing enforces. The workflow audits for it: every
invariant needs a test, every hard rule needs a gate, or it is honestly relabelled advisory.

## Security

**`jti`** — The JWT's unique token id, stored in Redis for the token's lifetime. Deleting
it is what makes logout real.

**Token family** — All refresh tokens descended from one login, sharing a `familyId`.
Reuse of a rotated token revokes the whole family.

**ES256** — ECDSA on P-256. Asymmetric, so verifiers hold only the public key and cannot
mint tokens. See [ADR-0005](../adr/0005-es256-jwt-jti-sessions.md).

**`kid`** — Key id in the JWT header, used to select the verification key. Makes rotation
possible without redeploying consumers.

**Fail open vs fail closed** — Cache reads fail **open** (Redis down → go upstream).
Session reads fail **closed** (Redis down → 401). A cache is an optimisation; a session
store is a security control.

## Documents

**Workflow** — `docs/workflows/WF-000-foundation.md`. What to build and why: domain
model, architecture, diagrams, acceptance criteria, risks. The specification.

**Prompt** — `docs/prompts/pokedex-api-prompt.md`. The order to build it in: phases,
steps, verification gates, recovery.

**ADR** — Architecture Decision Record. Context, decision, alternatives, consequences.
Immutable once accepted; superseded, never edited.

**IAR** — Information Asymmetry Register (workflow §3.0). Facts the author knows that an
implementer would otherwise miss. Each entry is a hypothesis until verified.

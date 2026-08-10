# pokedex-api

A Pokédex REST API on Java 24 / Spring Boot 4. It treats [PokeAPI](https://pokeapi.co/docs/v2)
as an upstream system of record and a local PostgreSQL store as a **curated replica** that
can be enriched with proprietary fields — and guarantees that re-synchronising never
destroys curator work.

This is a service. It ships no browser client. Consumers integrate through the published
OpenAPI contract and nothing else.

---

## Quick start

```bash
make keys                 # generate the dev ES256 keystore
docker compose up --build # postgres + redis + api
```

| What | Where |
|---|---|
| API | http://localhost:8080/api |
| OpenAPI contract | http://localhost:8080/api/v3/api-docs.yaml |
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| Health | http://localhost:8080/api/actuator/health |

Demo credentials (seeded, `dev` profile only): `demo` / `Demo123!` · `admin` / `Admin123!`

```bash
# default page size is 10; every row carries sprite, category, mass in kg, abilities
curl -s 'localhost:8080/api/v1/pokedex/pokemon' | jq '.content | length, .[0]'

# the cap is 100 — above it we reject rather than silently clamp
curl -s 'localhost:8080/api/v1/pokedex/pokemon?size=101' | jq .code

# the error contract — RFC 9457, not an ad-hoc blob
curl -s localhost:8080/api/v1/pokedex/local/9999 | jq
```

Longer version: [`docs/guides/quickstart.md`](docs/guides/quickstart.md).

---

## Why it is built this way

**PokeAPI's list endpoint returns only `{name, url}`.** No sprite, no mass, no abilities —
and the category lives on a third resource, `/pokemon-species/{id}`. A page of N rows costs
**1 + 2N upstream HTTP calls** — 21 at the default page size of 10, and 201 at the maximum
of 100. That single fact drives four decisions: Redis caching becomes load-bearing rather
than optional, the fan-out runs concurrently on virtual threads with bounded concurrency,
the page size is capped to bound the worst case, and local replication is the real answer.

**Re-synchronisation must never overwrite curator data.** Every field belongs to exactly
one of two disjoint sets — `Replicated` (authority: PokeAPI) or `Proprietary` (authority:
the curator). Because `Proprietary ∩ Replicated = ∅`, re-sync needs no conflict resolution
at all. The conflict was designed out rather than handled.

**The dependency rule is enforced by ArchUnit.** `domain` may not reference Spring, JPA,
Jakarta, or Jackson, and a violation fails the build — one step later than `javac` would
have, which is the trade [ADR-0001](docs/adr/0001-clean-architecture-layered-packages.md) makes explicit.

---

## Architecture

```
com.elatusdev.pokedex
├── catalog/       upstream read-through — US01, US02
├── pokedex/       curated local collection — US03, US04
├── identity/      users, tokens, sessions — WF-AUTH
└── shared/        the shared kernel — depends on nothing

each context carrying the four layers:
    domain/          model · vo · policy · exception · port   ← depends on nothing
    application/     usecase · command · result
    infrastructure/  persistence · pokeapi · cache · security
    web/             controller · error · config
```

**One Maven module.** The top of the tree is the bounded context
([ADR-0013](docs/adr/0013-bounded-context-packages.md)); the layers are packages inside each
one. Both the dependency rule and the context boundaries are enforced by ArchUnit rather
than by the compiler — see [ADR-0001](docs/adr/0001-clean-architecture-layered-packages.md),
which records what that costs.

Java 24 language level on a Temurin 25 (LTS) runtime · Spring Boot 4 · PostgreSQL 17 +
Flyway · Redis 7 · contract-first OpenAPI with generated interfaces and DTOs · RFC 9457
error envelope · ES256 JWT with `jti` sessions and refresh-token rotation.

Full picture: [`docs/diagrams/c4-context-container.md`](docs/diagrams/c4-context-container.md) ·
diagrams and formal model: [`docs/workflows/WF-000-foundation.md`](docs/workflows/WF-000-foundation.md).

---

## Build and test

```bash
make verify                                # every gate: build, tests, coverage, contract, e2e
mvn -B verify                              # the build. Never pass -DskipITs.
mvn -B test                                # fast inner loop — unit + WireMock + ArchUnit
mvn -B test -Dtest='*ArchitectureTest'     # architecture suite alone
mvn -B generate-sources                    # regenerate *Api + *DTO after a spec edit
```

Component tests use Testcontainers and need a running Docker daemon. Coverage must clear
90% line and 90% branch, enforced by JaCoCo, which fails the build below either threshold.
Everything runs locally — there is no pipeline, so `make verify` is the gate of record.
Details: [`docs/guides/build-and-test.md`](docs/guides/build-and-test.md).

---

## Documentation

Four buckets, one purpose each.

| Directory | Holds |
|---|---|
| [`docs/adr/`](docs/adr) | **Decisions.** Why the architecture is the way it is. Immutable once accepted |
| [`docs/diagrams/`](docs/diagrams) | **Models.** C4, ERD, state machines, sequences, flows — each with the rules it encodes |
| [`docs/handbook/`](docs/handbook) | **Patterns.** How to write code here: design, Java, streams, Spring, persistence, testing |
| [`docs/guides/`](docs/guides) | **Operations.** How to run it, test it, and debug it |
| [`docs/work-units/`](docs/work-units) | **Execution.** One work unit per phase, decomposed into activities |

| Doc | Read it when… |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | You are about to write code and need the rules |
| [`docs/workflows/`](docs/workflows) | You need the spec — seven workflows, one per story plus foundation and delivery |
| [`docs/prompts/pokedex-api-prompt.md`](docs/prompts/pokedex-api-prompt.md) | You are executing — phases, steps, verification gates, recovery |
| [`docs/work-units/`](docs/work-units) | You are implementing — activity-level detail with conventions, patterns, and anti-patterns |

> The Generative AI tools deliverable is a separate exercise, deferred to a later phase. It
> is not part of this repository's scope and nothing here depends on it.

### Handbook — coding patterns

| Doc | Covers |
|---|---|
| [Design patterns](docs/handbook/design-patterns.md) | Ports and adapters, policy objects, value objects, aggregates — and the patterns we deliberately avoid |
| [Java patterns](docs/handbook/java-patterns.md) | Records, sealed types, `Optional`, exceptions, immutability, **no Javadoc**, time |
| [Stream API](docs/handbook/stream-api.md) | Which pipelines we use, the anti-patterns, and when a loop is better |
| [Concurrency](docs/handbook/concurrency.md) | Bounded virtual-thread fan-out, why not reactive, timeouts |
| [Spring patterns](docs/handbook/spring-patterns.md) | Constructor injection and the Boot 4 trap, `RestClient`, transactions, thin controllers |
| [Persistence patterns](docs/handbook/persistence-patterns.md) | Three shapes, hard deletes, optimistic locking, Flyway, avoiding N+1 |
| [Error handling](docs/handbook/error-handling.md) | One throw per method, the translation layer, fail open vs fail closed |
| [Logging](docs/handbook/logging.md) | Levels, what never to log, the `traceId` bridge, and the fan-out volume trap |
| [Containerization](docs/handbook/containerization.md) | Layered jars, base image, JVM ergonomics, graceful shutdown, readiness vs health |
| [Testing pyramid](docs/handbook/testing-pyramid.md) | What each layer asserts, the `any()` ban, and mutation testing with PIT |

### Workflows — one per story

Each workflow is a **vertical slice**: what it delivers, the decisions specific to it, its
error paths, and its own acceptance criteria. Shared material — the IAR, the domain model,
the hard rules — lives in WF-000 and is referenced, never restated.

| Workflow | Delivers | Depends on |
|---|---|---|
| [WF-000 Foundation](docs/workflows/WF-000-foundation.md) | Project setup, contract, domain core, ArchUnit — **owns the shared spec** | — |
| [WF-AUTH User management](docs/workflows/WF-AUTH-user-management.md) | `/v1/security/**`, filter chain, route policy | WF-000 |
| [WF-US01 Pokémon enumeration](docs/workflows/WF-US01-pokemon-enumeration.md) | `GET /pokedex/pokemon` — paginated, cached, complete rows | WF-000 |
| [WF-US02 Detailed view](docs/workflows/WF-US02-detailed-view.md) | `GET /pokedex/pokemon/{idOrName}` | WF-US01 |
| [WF-US03 Data synchronization](docs/workflows/WF-US03-data-synchronization.md) | Persistence, the merge policy, `/pokedex/sync/**` | WF-000, WF-AUTH |
| [WF-US04 Local data modification](docs/workflows/WF-US04-local-data-modification.md) | Full CRUD over `/pokedex/local` | WF-US03, WF-AUTH |
| [WF-999 Delivery](docs/workflows/WF-999-delivery.md) | Docker, seed, E2E, the final audit | all |

### Work units — the implementation layer

The altitude ladder is **prompt → workflow → work unit → activity**. A work unit is one
coherent task; an activity is a single low-level step bound to specific conventions,
patterns, and anti-patterns, each ending in a verification. Order:
[work-unit DAG](docs/diagrams/work-unit-dag.md).

| WU | Delivers | Workflow |
|---|---|---|
| [WU-000-A](docs/work-units/WU-000-A-project-setup.md) | One module, four contexts, every gate wired | WF-000 |
| [WU-000-B](docs/work-units/WU-000-B-contract.md) | OpenAPI document, generated interfaces | WF-000 |
| [WU-000-C](docs/work-units/WU-000-C-domain-core.md) | Value objects, aggregates, state machine, ports | WF-000 |
| [WU-000-D](docs/work-units/WU-000-D-architecture-tests.md) | 22 ArchUnit rules | WF-000 |
| [WU-AUTH-A](docs/work-units/WU-AUTH-A-user-domain.md) | `User` aggregate, refresh-token families | WF-AUTH |
| [WU-AUTH-B](docs/work-units/WU-AUTH-B-security-adapters.md) | ES256, BCrypt, `jti` sessions | WF-AUTH |
| [WU-AUTH-C](docs/work-units/WU-AUTH-C-auth-endpoints.md) | Endpoints and deny-by-default routing | WF-AUTH |
| [WU-US01-A](docs/work-units/WU-US01-A-catalog-adapter.md) | Bounded fan-out, mapper, failure modes | WF-US01 |
| [WU-US01-B](docs/work-units/WU-US01-B-cache.md) | Redis cache, fail-open | WF-US01 |
| [WU-US01-C](docs/work-units/WU-US01-C-list-endpoint.md) | List use case and endpoint | WF-US01 |
| [WU-US02-A](docs/work-units/WU-US02-A-detail-mapping.md) | Evolution tree, description, localised names | WF-US02 |
| [WU-US02-B](docs/work-units/WU-US02-B-detail-endpoint.md) | Detail use case and endpoint | WF-US02 |
| [WU-US03-A](docs/work-units/WU-US03-A-persistence.md) | Flyway schema, JPA adapters | WF-US03 |
| [WU-US03-B](docs/work-units/WU-US03-B-sync-use-cases.md) | Merge policy and sync use cases | WF-US03 |
| [WU-US03-C](docs/work-units/WU-US03-C-sync-endpoints.md) | Sync endpoints and seed data | WF-US03 |
| [WU-US04-A](docs/work-units/WU-US04-A-crud-use-cases.md) | CRUD use cases, optimistic locking | WF-US04 |
| [WU-US04-B](docs/work-units/WU-US04-B-crud-endpoints.md) | CRUD endpoints and the error contract | WF-US04 |
| [WU-999-A](docs/work-units/WU-999-A-containerisation.md) | Docker, compose, contract check, E2E | WF-999 |
| [WU-999-B](docs/work-units/WU-999-B-verification.md) | Clean-clone verify, AC audit, report | WF-999 |

### Diagrams

| Group | Diagrams |
|---|---|
| Architecture | [C4 context and containers](docs/diagrams/c4-context-container.md) · [Package dependencies](docs/diagrams/package-dependencies.md) · [Element relationships](docs/diagrams/element-relationships.md) · [Deployment](docs/diagrams/deployment.md) |
| Domain | [Aggregates](docs/diagrams/domain-aggregates.md) · [Entity relationships](docs/diagrams/entity-relationship.md) · [Replication state machine](docs/diagrams/replication-state-machine.md) |
| Flows | [Listing a page](docs/diagrams/sequence-list-page.md) · [Re-sync merge](docs/diagrams/sequence-resync-merge.md) · [Data flow](docs/diagrams/data-flow-sync.md) · [Read path decision tree](docs/diagrams/read-path-decision-tree.md) |
| Errors | [Re-sync](docs/diagrams/error-paths-resync.md) · [Batch sync](docs/diagrams/error-paths-batch-sync.md) |
| Security | [Auth filter chain](docs/diagrams/auth-filter-chain.md) · [Refresh token rotation](docs/diagrams/refresh-token-rotation.md) |
| Delivery | [Contract distribution](docs/diagrams/contract-distribution.md) · [Verification gates](docs/diagrams/verification-gates.md) · [ArchUnit enforcement](docs/diagrams/archunit-enforcement.md) · [Work unit DAG](docs/diagrams/work-unit-dag.md) |

### Guides — running and debugging

| Guide | Read it when… |
|---|---|
| [Quickstart](docs/guides/quickstart.md) | You want it running in ten minutes |
| [OpenAPI contract-first](docs/guides/openapi-contract-first.md) | You are adding or changing an endpoint |
| [Contract consumers](docs/guides/contract-consumers.md) | You are changing a response shape and something depends on it |
| [Security & auth](docs/guides/security-auth.md) | You are touching tokens or route protection |
| [Build and test](docs/guides/build-and-test.md) | You want to know what to run before you commit |
| [ArchUnit governance](docs/guides/archunit-governance.md) | The build went red with an architecture violation |
| [Troubleshooting](docs/guides/troubleshooting.md) | Something is broken |
| [Glossary](docs/guides/glossary.md) | A term is unfamiliar |

### Architecture Decision Records

Immutable once accepted. If a decision turns out to be wrong, add a new ADR that supersedes
it — never edit or delete the original.

| ADR | Title | Status |
|---|---|---|
| [0001](docs/adr/0001-clean-architecture-layered-packages.md) | Clean Architecture as layered packages in one Maven module | Accepted |
| [0002](docs/adr/0002-contract-first-openapi.md) | Contract-first OpenAPI with generated interfaces and DTOs | Accepted |
| [0003](docs/adr/0003-rfc9457-problemdetail.md) | RFC 9457 ProblemDetail instead of the house ApiError envelope | Accepted |
| [0004](docs/adr/0004-java-24-language-level.md) | Java language level 24 on a Temurin 25 runtime | Accepted |
| [0005](docs/adr/0005-es256-jwt-jti-sessions.md) | ES256 JWT with Redis `jti` sessions and refresh-token rotation | Accepted |
| [0006](docs/adr/0006-redis-cache-pokeapi-fanout.md) | Redis cache and bounded virtual-thread fan-out for PokeAPI | Accepted |
| [0007](docs/adr/0007-proprietary-field-merge-policy.md) | Re-synchronisation merges — proprietary fields are never overwritten | Accepted |
| [0008](docs/adr/0008-openapi-contract-distribution.md) | Publishing the OpenAPI contract as a versioned artifact | Accepted |
| [0009](docs/adr/0009-no-bundled-client.md) | The service ships no client | Accepted |
| [0010](docs/adr/0010-hard-deletes.md) | Deletes are hard | Accepted |
| [0011](docs/adr/0011-container-image-strategy.md) | A hand-written layered Dockerfile, not buildpacks | Accepted |
| [0012](docs/adr/0012-flyway-versioned-migrations.md) | Flyway versioned migrations, not `ddl-auto` | Accepted |
| [0013](docs/adr/0013-bounded-context-packages.md) | Packages by bounded context, layers inside them | Accepted |

**The three worth reading first.** [0007](docs/adr/0007-proprietary-field-merge-policy.md)
answers a conflict the requirements never state but every correct implementation must
resolve. [0009](docs/adr/0009-no-bundled-client.md) records why this repository ships no
browser client — a colocated client absorbs breaking API changes silently, so the contract
is never tested as a contract. [0008](docs/adr/0008-openapi-contract-distribution.md) is the
obligation that separation creates.

To add one: copy `docs/adr/0000-adr-template.md`, number it sequentially, fill in every
section — **Context matters most** — set status `Proposed` then `Accepted` after review,
update the table above, and commit with `docs(adr): record decision on {topic}`.

---

*The workflow is the specification. The ADRs are the reasoning. `CLAUDE.md` is the law.*

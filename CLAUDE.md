# CLAUDE.md — pokedex-api

Directives for any agent or human implementing in this repository. These are rules,
not suggestions. Code that ignores them has **negative** value: it looks like progress
and has to be redone.

Read this file, then [`docs/workflows/WF-000-foundation.md`](docs/workflows/WF-000-foundation.md)
(what to build and why), then [`docs/prompts/pokedex-api-prompt.md`](docs/prompts/pokedex-api-prompt.md)
(the order to build it in). Decisions already made live in [`docs/adr/`](./README.md) —
do not relitigate them in code; supersede them with a new ADR if they are wrong.

---

## Execution rules

| # | Rule | What it means here |
|---|---|---|
| 1 | Read the standards first | This file plus the workflow §9 Hard Rules, fully, before the first edit |
| 2 | Never be fast | Speed produces rework. If the process feels slow, that is correct |
| 3 | Never skip steps | No skipped verification, no skipped docs |
| 4 | Never batch without verifying | Edit one file, verify it, then the next |
| 5 | Document before code | No rule covers the pattern you need? Add the rule first, then write the code |
| 6 | **Tests precede implementation** | See [TDD is not optional](#tdd-is-not-optional) below |
| 7 | Full build before push | `mvn -B verify` — `mvn compile` is **not** enough. "It compiles" ≠ "it's green" |

---

## TDD is not optional

Every production class in `..domain..` and `..application..` is written test-first. Not
"tested afterwards" — **written second**.

### The loop

1. **Red.** Write one failing test that names the behaviour you are about to add. Run it and
   **watch it fail**. A test that has never failed has not been shown to test anything.
2. **Green.** Write the least code that passes it. Not the design you have in mind — the
   least code. The design emerges from the next few cycles.
3. **Refactor.** Now improve the shape, with the test holding you.

Commit red and green together, implementation last in the diff. `git log --oneline` is the
evidence, and it is the thing a reviewer can check.

### What "test first" means concretely

| Situation | Test first looks like |
|---|---|
| A value object | The invalid case **before** the constructor exists — `assertThatThrownBy(() -> new Mass(0))` |
| A use case | Stub the ports, assert the outcome and the interactions, then write the class |
| A state machine | Every legal edge **and every illegal edge**, before `transitionTo` exists |
| A policy | A property over generated inputs, before the policy — this is how F7 was proven |
| A bug fix | A failing test that reproduces it. Fixing first and testing after proves nothing |

### Where TDD does not apply

Adapters and configuration are driven by their contract tests, not unit-first: a WireMock
stub or a Testcontainers fixture comes first, then the adapter. Wiring, `@Configuration`, and
generated code are not test-driven — there is no behaviour to drive.

### How this is checked

It cannot be enforced by a build gate, and pretending otherwise would be cheap talk. It is
checked three ways:

- **Mutation testing.** Code written after its test survives fewer mutants. A low mutation
  score on `..domain..` is the strongest available signal that tests were retrofitted.
- **Commit order.** Test and implementation in one commit, test first in the diff.
- **Review.** "Show me the commit where this test failed" is a fair question.

---

## The one rule

`..domain..` depends on nothing.

Not Spring, not JPA, not Jakarta, not Jackson, not another layer.

This is **one Maven module**, so the compiler will not stop you — `import
org.springframework...` in a domain class compiles fine. **ArchUnit `L2` fails the build
instead.** That is the trade [ADR-0001](docs/adr/0001-clean-architecture-layered-packages.md) makes
explicit, and it is why the architecture suite is not optional.

If you need a framework type in a domain class, you have found a design error, not a missing
dependency. Define a port and implement it in `infrastructure`.

---

## Where things live

| Thing | Path | Enforced by |
|---|---|---|
| Aggregates, value objects, policies, ports | `domain/…/domain/{model,vo,policy,port,exception}` | `DomainPurityArchitectureTest` |
| Use cases — one class per operation | `application/…/application/usecase` | `N1` |
| JPA entities and Spring Data repositories | `infrastructure/…/persistence` | `N3`, `N4`, `IO1` |
| PokeAPI client | `infrastructure/…/pokeapi` | `IO2` |
| Controllers implementing generated `*Api` | `web/…/web/controller` | `N2`, `OA1` |
| The OpenAPI contract | `src/main/resources/openapi/pokedex-api.yaml` | `OA1` |
| Flyway migrations | `src/main/resources/db/migration` | Fails fast on checksum drift |
| ArchUnit rules | `src/test/java/com/elatusdev/pokedex/architecture` | Itself |

---

## Backend rules

### Layering

- Controllers are **thin**: bind, delegate, map. Zero business logic.
- One use case class per operation. `@Service @Transactional` — the use case is the transaction boundary.
- Use cases must not import `org.springframework.web..` and must not return `*DataModel`.
- Cross-aggregate references are **id-only**. Never modify two aggregates in one transaction.
- Every `@RestController` implements a generated `*Api`. Hand-written endpoints fail `OA1`.

### Java

- **Records** for immutable data. Value objects in `domain.vo` are records without exception.
- **`Optional`** at return boundaries. Never `null` for absence, never `null` for an empty collection.
- **One `throw` per method**, a specific domain exception, translated at the `@RestControllerAdvice`.
- Never catch `Exception` or `Throwable`. No empty catch blocks — log, rethrow, or wrap.
- Constructor injection only. **A bean with ≥2 constructors annotates the production one `@Autowired`** — Spring Boot 4 dropped the primary-constructor heuristic and the failure is invisible at compile time.
- `Stream.toList()`, text blocks for multi-line SQL/JSON, pattern matching over `instanceof` chains.
- IDs are `Long`. The one exception is `pokeApiId`, an upstream identifier we do not own.
- Dates: `java.time.Month`, never a bare int month. `.now()` always takes a `Clock` or `ZoneId`.
- Size caps: method body ≤ 20 lines, class ≤ 300 (500 for entities, config, controllers), cyclomatic ≤ 10, nesting ≤ 3. Use guard clauses.
- Copyright header `Copyright (c) 2026 ElatusDev` in the first 10 lines of every `.java`.
- **No Javadoc.** Not on public API, not anywhere. A comment that restates the signature goes stale and misleads. Names carry *what*, tests carry *how*, ADRs carry *why*. A short `//` comment explaining a non-obvious **why** is fine — if deleting it loses no information, it was noise. Generated sources excluded.
- **Logging**: `domain` has no logger and does not log. Never log a token, password hash, PII, or full body. Log the fan-out as a **summary**, not per call. Log *or* rethrow, never both. See [logging](docs/handbook/logging.md).

### Spring

- `RestClient` for HTTP. `RestTemplate`, `WebClient`, and WebFlux are forbidden.
- `@ConfigurationProperties` records for config. No scattered `@Value`.
- Optimistic locking with `@Version` by default. No remote I/O inside a transaction.
- Every list endpoint takes `Pageable`. Default size **10**, maximum **100**. A larger `size` is a 400 `INVALID_PAGINATION` — **reject, never silently clamp**.
- Bind to request DTOs, never to entities — mass assignment is OWASP A08.

### Security

- Deny by default. Every `SecurityFilterChain` terminates in `.anyRequest()`.
- ES256 only. Reject `alg: none`. Validate `iss`, `aud`, `exp`. Resolve keys by `kid`.
- No PII in JWT claims — signed is not encrypted.
- BCrypt for passwords. SHA-256 is not a password hash.
- No secrets in `*.properties`, no secrets in `ARG`/`ENV`, no PII or tokens in logs.
- Cache reads **fail open**. Session reads **fail closed**. Never confuse the two.

---

## Contract rules

The OpenAPI document is a **published interface**, not an internal file. Consumers you
cannot see derive their types from it.

- The authored spec and the served `/v3/api-docs.yaml` must be byte-identical (AC1c).
- A breaking change to an existing operation requires a new path version. `oasdiff` gates it.
- Every release attaches `pokedex-api.yaml` as an asset. A release without one is a failed release.
- Before changing a response shape, ask what it does to a consumer. The compiler will not.
- CORS is an explicit origin allow-list. Never `*`.

See [ADR-0008](docs/adr/0008-openapi-contract-distribution.md).

---

## Testing

| Tier | Naming | Runner | Where |
|---|---|---|---|
| Unit | `*Test.java` | Surefire | Alongside the package under test |
| Component | `*ComponentTest.java` | Failsafe + Testcontainers | `src/test/java/.../component` |
| Architecture | `*ArchitectureTest.java` | Surefire | `src/test/java/.../architecture` |
| Mutation | PIT, `make mutation` | pitest-maven | `domain` (85%), `application` (75%) |
| API E2E | Newman collection | Newman | `e2e/` |

Rules:

- Every test asserts **state** (return value or exception) **and interactions** — exact args, explicit `times(1)`, `verifyNoMoreInteractions`.
- **Never `any()`, `anyString()`, `anyLong()`** in a stub or a verification. They pass when the code uses the *wrong* value, which is the defect you were trying to catch. Use the exact expected value; if some component is genuinely unknowable, use `argThat` with a real predicate over everything else.
- Never assert `!= null`. Assert the actual value.
- Never `Thread.sleep`. Never a disabled test without a written rationale.
- Test names read `should_X_when_Y`.
- Every error row in workflow §9.5 has a test asserting the exact status **and** the `code`.

---

## Quality gates

New code must clear all of these before a push:

| Gate | Threshold |
|---|---|
| Line coverage | ≥ 90% |
| Branch coverage | ≥ 90% |
| Mutation score — `domain` | ≥ 85% (`make mutation`, not in the commit loop) |
| Mutation score — `application` | ≥ 75% |
| Duplication (new code) | ≤ 1% |
| New blocker / critical / major violations | 0 |
| New code smells | ≤ 5 |
| Security hotspots reviewed | 100% |
| HIGH/CRITICAL CVEs | 0 |
| Secrets detected (`gitleaks`) | 0 |
| ArchUnit rules | all pass, none frozen |

### Suppression ladder

- `sonar.exclusions` and `sonar.coverage.exclusions` — **forbidden**
- `// NOSONAR` — **forbidden**
- `/** … */` Javadoc — **forbidden** in `src/main/java`; a grep target in `make verify` fails on it
- `@SuppressWarnings("java:SNNNN")` with a one-line WHY comment, narrowest possible scope, one rule at a time — **acceptable**

`FreezingArchRule` is forbidden. Rules ship enforced or they do not ship.

---

## Commands

```bash
make verify                                # every gate — the command of record
make mutation                              # PIT on domain + application. Slow; run deliberately
mvn -B verify                              # the build. Never pass -DskipITs.
mvn -B test     # fast inner loop
mvn -B test -Dtest='*ArchitectureTest'     # architecture suite alone
mvn -B generate-sources                    # regenerate *Api + *DTO after a spec edit
docker compose up --build                  # postgres + redis + api
```

---

## Gotchas that will bite you

| Symptom | Cause |
|---|---|
| `BeanInstantiationException` at boot, compiles fine | A bean has two constructors and neither is `@Autowired` (Spring Boot 4) |
| Compile error on `getAllValidationResults()` | Renamed to `getParameterValidationResults()` in Framework 7 |
| Half your 400s come back as 500 | Parameter validation throws **two** exception types; map both |
| Endpoints grouped by the wrong tag in Swagger | `useTags=false` groups by first path segment; re-tagging does nothing |
| Bulbasaur weighs 69 kg | `weight` is hectograms, `height` is decimetres |
| Description renders with visible artefacts | `flavor_text` contains literal `\n` and `\f` |
| Eevee shows one evolution | The chain is a recursive tree, not a list — 8 branches |
| Coverage gate fails with zero new issues | Editing one line inside pre-existing duplicated code counts against `new_duplicated_lines_density` |
| `make verify` fails at the component tier | Testcontainers needs a running Docker daemon. The build fails rather than skips, deliberately |
| A consumer breaks after a spec change | It pinned an older contract version. Release, and let it adopt deliberately |

---

**Canonical references:**
[`docs/workflows/WF-000-foundation.md`](docs/workflows/WF-000-foundation.md) (what and why) ·
[`docs/prompts/pokedex-api-prompt.md`](docs/prompts/pokedex-api-prompt.md) (build order) ·
[`README.md`](./README.md) (decisions and the handbook index)

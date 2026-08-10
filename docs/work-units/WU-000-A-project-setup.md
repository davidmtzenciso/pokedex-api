# WU-000-A — Project Setup

| Field | Value |
|---|---|
| **Work Unit** | WU-000-A |
| **Parent** | [WF-000 Foundation](../workflows/WF-000-foundation.md) |
| **Objective contribution** | An empty but fully governed project. Every gate that will ever run is wired now |
| **Estimate** | M |
| **Status** | **done** — see [Current state](#current-state) |

## Objective

Produce a single-module Maven project with the four layer packages in place and every
quality gate already enforced — before there is any code to enforce it against.

## Entry Criteria

- JDK 25 on the path, `mvn -v` reports Maven 3.9+
- Docker daemon running (needed later, for the component tier)
- Branch `feat/pokedex-api-1` checked out from a clean tree

## Inputs

| Input | Source | Used by |
|---|---|---|
| Package layout | [package-dependencies.md](../diagrams/package-dependencies.md) | A2 |
| Gate list | [verification-gates.md](../diagrams/verification-gates.md) | A3–A5 |
| Language level decision | [ADR-0004](../adr/0004-java-24-language-level.md) | A1 |
| Single-module decision | [ADR-0001](../adr/0001-clean-architecture-layered-packages.md) | A1, A2 |

## Outputs

- One `pom.xml` that builds green with zero sources
- JaCoCo, enforcer, and the source-hygiene gate active and **demonstrably failing** when violated
- `Makefile` with `verify`, `keys`, `up`, `down`, `e2e`, `contract-check`, `mutation`

---

## ▶ Activity Sequence

### A1 — Create the POM

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `pom.xml` |
| **Intent** | One file that pins every version |
| **Depends on** | — (entry) |
| **Status** | done |

**How**
`packaging=jar`, `maven.compiler.release=24`. Import `spring-boot-dependencies` and
`testcontainers-bom` in `<dependencyManagement>`. **No version ranges anywhere** — every
dependency and plugin pins an exact version. No parent POM.

**Conventions**
- Exact versions only; ranges make builds irreproducible → [build and test](../guides/build-and-test.md)
- Language level 24 on a Temurin 25 runtime → [ADR-0004](../adr/0004-java-24-language-level.md)

| Field | Value |
|---|---|
| **Produces** | `pom.xml` |
| **Verify** | `mvn -B validate` |
| **Pass when** | Exit 0; `mvn help:evaluate -Dexpression=maven.compiler.release` prints `24` |
| **On fail / Rollback** | `git checkout pom.xml` |

### A2 — Create the four layer packages

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `src/main/java/com/elatusdev/pokedex/{domain,application,infrastructure,web}` |
| **Intent** | The layers exist before anything is put in them |
| **Depends on** | A1 |
| **Status** | done |

**How**
`domain/{model,vo,policy,exception,port}` · `application/{usecase,command,result}` ·
`infrastructure/{persistence,pokeapi,cache,security}` · `web/{controller,error,config}`.

> One module means the compiler will **not** stop a domain class importing Spring.
> `DomainPurityArchitectureTest` (WU-000-D) is the only thing that will — see
> [ADR-0001](../adr/0001-clean-architecture-layered-packages.md).

**Avoid**
- Putting a framework annotation in `..domain..` "just for DI". Define a port instead → [design patterns](../handbook/design-patterns.md)

| Field | Value |
|---|---|
| **Produces** | The package tree |
| **Verify** | `mvn -B test -Dtest=DomainPurityArchitectureTest` (once WU-000-D lands) |
| **Pass when** | Green; a deliberate `import org.springframework...` in `..domain..` turns it red |
| **On fail / Rollback** | Move the offending class out of `..domain..` |

### A3 — Wire JaCoCo with a failing threshold

| Field | Value |
|---|---|
| **Type** | config |
| **Target** | `pom.xml` |
| **Intent** | The coverage gate must exist from commit one, or it becomes a cliff later |
| **Depends on** | A1 |
| **Status** | done |

**How**
`prepare-agent` (unit) and `prepare-agent-integration` (component), then `merge` both
`.exec` files, `report`, and `check` with `LINE` and `BRANCH` at `0.90`, all bound to
`verify`. Thresholds are properties (`jacoco.line.min`, `jacoco.branch.min`) so a
deliberate probe can lower them without editing the POM.

**Conventions**
- Merge tiers before reporting; one tier under-counts → [build and test](../guides/build-and-test.md)

| Field | Value |
|---|---|
| **Produces** | Coverage gate active |
| **Verify** | `mvn -B verify` with an uncovered class present |
| **Pass when** | The build **fails** on the JaCoCo check |
| **On fail / Rollback** | Revert the plugin block |

### A4 — Source-hygiene gate

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `scripts/check-source-hygiene.sh`, `pom.xml` |
| **Intent** | Three gates that fail even when the code is perfect |
| **Depends on** | A1 |
| **Status** | done |

**How**
One bash script run by `exec-maven-plugin` at the `validate` phase, checking:

1. `Copyright (c) 2026 ElatusDev` in the first 10 lines of every `.java`
2. **No `/**` anywhere in `src/main/java`** — Javadoc is forbidden ([java patterns](../handbook/java-patterns.md#no-javadoc))
3. No `// NOSONAR` — the suppression ladder

One script rather than three plugins, because all three are greps and a single failure
message is easier to act on. Generated sources are excluded.

**Avoid**
- Trusting the gate without breaking it first. A gate nobody has seen fail is a gate nobody knows works

| Field | Value |
|---|---|
| **Produces** | Header, Javadoc, and suppression gates |
| **Verify** | Introduce each violation in turn, run `mvn -B validate` |
| **Pass when** | Each **fails the build** naming the file, and a clean tree prints `source hygiene: OK` |
| **On fail / Rollback** | Revert the execution block |

### A5 — Maven enforcer

| Field | Value |
|---|---|
| **Type** | config |
| **Target** | `pom.xml` |
| **Intent** | Conflicting transitive versions blocked at `validate` |
| **Depends on** | A1 |
| **Status** | done |

**How**
`dependencyConvergence`, `requireReleaseDeps`, `requireMavenVersion [3.9,)`.

| Field | Value |
|---|---|
| **Produces** | Convergence gate |
| **Verify** | `mvn -B validate` |
| **Pass when** | Green on the current tree |
| **On fail / Rollback** | Pin the conflicting version explicitly |

### A6 — Boot application and its first component test

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `web/…/PokedexApplication.java`, `…/ApplicationContextLoadsComponentTest.java` |
| **Intent** | Catch Spring Boot 4 multi-constructor regressions the moment they appear |
| **Depends on** | A2 |
| **Status** | **partial** — the application class exists; the context test is not written |

**How**
`@SpringBootApplication` plus a `@SpringBootTest` asserting the context loads. This is the
cheapest guard against IA7 — a bean with two constructors and no `@Autowired` fails here
rather than three work units later in a MockMvc test.

**Patterns**
- Production ctor carries `@Autowired` when ≥2 exist → [spring patterns](../handbook/spring-patterns.md)

| Field | Value |
|---|---|
| **Produces** | A runnable, testable Boot app |
| **Verify** | `mvn -B verify` |
| **Pass when** | Context loads, test green |
| **On fail / Rollback** | Read the `BeanInstantiationException` — it is almost always IA7 |

### A7 — Makefile

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `Makefile`, `.gitignore` |
| **Intent** | One command to remember, because a list of commands is a list nobody runs |
| **Depends on** | A3, A4 |
| **Status** | done |

**How**
Targets: `verify` (the full chain), `keys`, `up`, `down`, `e2e`, `contract-check`,
`mutation`, `clean`. `.gitignore` **must** exclude `keys/` — a committed keystore is exactly
what `gitleaks` exists to catch.

Targets that need tooling not yet built (`e2e`, `contract-check`) fail with a clear message
rather than silently succeeding.

| Field | Value |
|---|---|
| **Produces** | `make verify` runnable |
| **Verify** | `make verify` |
| **Pass when** | Green on the current tree |
| **On fail / Rollback** | — |

---

## Current state

Built and verified in this repository:

| Item | State |
|---|---|
| `pom.xml`, four layer packages | done — compiles on Spring Boot 4.1.0, JDK 25, release 24 |
| JaCoCo 90/90 merged gate | wired; thresholds are overridable properties |
| Source hygiene (copyright · no-Javadoc · no-NOSONAR) | **proven** — each violation was introduced, seen to fail the build, then reverted |
| Maven enforcer | wired |
| `PokedexApplication` | exists |
| `ApplicationContextLoadsComponentTest` | **not yet written** (A6) |
| `Makefile`, `.gitignore` | done |

Coverage is currently **below** the 90/90 gate because only the value objects and the state
machine exist. That is expected until WU-000-C completes.

## Exit Criteria

- [x] Project builds
- [x] Each hygiene gate demonstrated to fail on a real violation, then reverted
- [x] `keys/` is gitignored
- [ ] `ApplicationContextLoadsComponentTest` green (blocked: needs a Docker daemon for the component tier)
- [ ] Coverage gate satisfied (blocked: needs WU-000-C)

```bash
make verify
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC1, AC9b, AC9c |
| Decision | [ADR-0004](../adr/0004-java-24-language-level.md), [ADR-0001](../adr/0001-clean-architecture-layered-packages.md) |

## Blocks

WU-000-B, WU-000-C, WU-000-D

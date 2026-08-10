# Build and Test — what to run before you commit

So the build tells you the truth in ninety seconds, because nothing downstream will.

**Read it when…** you are about to commit, or a gate went red and you want to reproduce it.

> Canonical specs (don't restate — link):
> [`../../CLAUDE.md`](../../CLAUDE.md) (the rules), [`../workflows/WF-000-foundation.md`](../workflows/WF-000-foundation.md) §10 (acceptance criteria).

---

## The one command

```bash
make verify
```

It chains every gate in [verification-gates.md](../diagrams/verification-gates.md):
compile, ArchUnit, unit, upstream contract, component, coverage, contract check, compose,
and E2E.

> **There is no pipeline.** This project builds and runs entirely locally, so `make verify`
> is the gate of record. That has one honest weakness worth naming: nothing makes it
> unskippable. A pre-push git hook helps; discipline does the rest.

## Maven directly

```bash
mvn -B verify                              # compile → archunit → unit → component → coverage
mvn -B test     # seconds — the TDD loop
mvn -B test -Dtest='*ArchitectureTest'     # the structural suite alone
mvn -B verify       # component tier for one module
mvn -B generate-sources                    # after any spec edit
```

| Rule | Why |
|---|---|
| `-am` is not needed | One module — there is nothing to also-make |
| `mvn compile` is **not** enough | "It compiles" ≠ "it's green" |
| Never pass `-DskipITs` or `-Dmaven.failsafe.skip=true` | Both are BLOCKER violations (`TBX-001..003`) |
| Touched `..domain..`? | Everything depends on it — run the full suite, not one test |

## Test tiers

| Tier | Naming | Runner | Needs Docker? | What it proves |
|---|---|---|:---:|---|
| Unit | `*Test.java` | Surefire | no | Value objects, policies, use cases against mocked ports |
| Upstream contract | WireMock-backed | Surefire | no | The PokeAPI adapter against recorded shapes, including 500s and timeouts |
| Component | `*ComponentTest.java` | Failsafe + Testcontainers | **yes** | Full Spring context against a real Postgres and Redis |
| Architecture | `*ArchitectureTest.java` | Surefire | no | The 16 structural rules |
| Published contract | `make contract-check` | `openapi-spec-validator` + `oasdiff` | no | The spec is valid, and no breaking change slipped into an existing operation |
| API E2E | Newman collection | `make e2e` | yes | Every endpoint against the running stack with seeded data |
| Mutation | PIT | `make mutation` | no | That the tests would **notice** if the code were wrong |

> **Component tests need a running Docker daemon**, and `make verify` **fails** rather than
> skips when it is absent. A skipped tier that reports green is worse than a red build —
> that is risk R8, and the mitigation is refusing to pretend.

Full patterns per layer: [testing pyramid](../handbook/testing-pyramid.md).

## Coverage

JaCoCo merges Surefire and Failsafe execution data before reporting, because coverage from
one tier under-counts significantly.

| Metric | Threshold | Enforced by |
|---|---|---|
| Line coverage | ≥ 90% | JaCoCo `check` goal — **fails the build** |
| Branch coverage | ≥ 90% | JaCoCo `check` goal — **fails the build** |

Nothing else is claimed. Duplication ratios, code smells, and security-hotspot review
require a hosted analysis server, and this project has none — so those gates were **deleted
rather than restated as aspirations**. A threshold nobody measures is cheap talk.

```bash
mvn -B verify && open target/site/jacoco/index.html
```

### Coverage is not the real question

Coverage says a line executed. It does not say an assertion would have failed if that line
were wrong. **Mutation testing** answers that:

```bash
make mutation      # PIT rewrites the bytecode and reruns the suite per mutant
open target/pit-reports/index.html
```

| Module | Threshold |
|---|:---:|
| `domain` | 85% |
| `application` | 75% |

It is **not** part of `make verify` — it takes minutes, and a slow commit loop is a
disabled commit loop. Run it after finishing a domain class, before opening a review, and
any time coverage looks suspiciously high. See
[testing pyramid](../handbook/testing-pyramid.md#mutation-testing--proving-the-tests-actually-test).

## Contract check

```bash
make contract-check
```

Runs `openapi-spec-validator` on the committed spec, then `oasdiff` against the spec at the
last git tag. A breaking change to an existing operation fails it — see
[contract consumers](contract-consumers.md) for what counts as breaking.

## Gates that fail even when the code compiles

| Gate | Failure looks like |
|---|---|
| File header | Any `.java` outside generated sources whose first line is not its `package` declaration. Bound to the `validate` phase |
| Dependency convergence | Maven enforcer blocks conflicting transitive versions |
| Suppression ladder | A grep target fails on `// NOSONAR` or `sonar.exclusions` |
| Javadoc | A grep target fails on `/**` in `src/main/java` outside generated sources — see [java patterns](../handbook/java-patterns.md#no-javadoc) |
| `gitleaks detect` | Any committed secret. Run it locally — the dev keystore password is exactly what gets committed by accident |

## Test writing rules

- Assert **state and interactions** — exact args, explicit `times(1)`, `verifyNoMoreInteractions`.
- **Never `any()`** in a stub or verification. It passes when the code uses the wrong value. Exact values, or `argThat` with a real predicate.
- Never assert `!= null`. Assert the value.
- Never `Thread.sleep`. Inject `ClockPort` instead.
- Name tests `should_X_when_Y`.
- Every error row in workflow §9.5 has a test asserting the exact status **and** the `code`.

---

**Canonical specs (don't restate — link):**
[`CLAUDE.md`](../../CLAUDE.md) (quality gates and suppression ladder) ·
[WF-000 §10](../workflows/WF-000-foundation.md) (acceptance criteria) ·
[verification gates](../diagrams/verification-gates.md)

# ADR-0001: Clean Architecture as Layered Packages in One Maven Module

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

The exercise explicitly grades "Clean Architecture: separation of concerns and independence of components." Two questions follow, and they are usually conflated:

1. **What are the layers?** — settled, and not controversial: domain, application, infrastructure, web, with dependencies pointing inward.
2. **What stops someone violating them?** — the actual decision.

Most submissions answer the second question with "code review". That is not a mechanism. A single misplaced `import org.springframework...` in a domain class compiles perfectly and is invisible unless a reviewer happens to notice it on the day.

There are two real mechanisms available. A Maven module per layer makes a violation a **compile error**, because `domain`'s POM simply would not have Spring on its classpath. ArchUnit makes it a **build failure**, seconds later, by asserting on compiled bytecode.

## Decision

**Four layered packages in a single Maven module**, with the dependency rule enforced by ArchUnit.

```
com.elatusdev.pokedex
├── domain/          model · vo · policy · exception · port    ← depends on nothing
├── application/     usecase · command · result                ← depends on: domain
├── infrastructure/  persistence · pokeapi · cache · security   ← depends on: application, domain
└── web/             controller · error · config                ← depends on: application, infrastructure

The top of that tree is the **bounded context**, not the layer — each of `catalog`,
`pokedex`, `identity` and `shared` carries these four packages
([ADR-0013](0013-bounded-context-packages.md)). The dependency rule below is unchanged by
that: ArchUnit's `..domain..` matches every context's domain equally.
```

Ports (`PokemonRepository`, `PokemonCatalog`, `CachePort`, `TokenIssuer`, `PasswordHasher`, `ClockPort`) are interfaces owned by `domain`. Adapters in `infrastructure` implement them. The dependency arrow points inward at every boundary — that part is unchanged by the packaging choice.

The ArchUnit suite lives in `src/test/java/.../architecture/` and imports `target/classes`. Two rules carry the weight the module graph would otherwise have carried:

- **`L2` (domain purity)** asserts that `..domain..` depends on no `org.springframework..`, `jakarta..`, `org.hibernate..`, or `com.fasterxml..` type. This is the rule that replaces the missing POM boundary, and it is the single most important test in the suite.
- **`L1` (layer direction)** is a full `layeredArchitecture()` rule rather than pairwise checks, so a newly added package cannot quietly sit outside the model.

Both are proven against deliberate violations during WU-000-D: a misplaced import must turn the build red before the rule is trusted.

## Alternatives Considered

1. **A Maven module per layer** (`domain`, `application`, `infrastructure`, `web`, `architecture-tests`) — The strongest enforcement available: `domain/pom.xml` declares no Spring dependency, so the violation cannot compile. The compiler does not need to be persuaded, and the module a class lives in tells a reader what it may touch.

   Rejected on proportionality. For roughly a hundred production classes, one deployable and one team, five POMs buy exactly one property over ArchUnit — feedback at `javac` rather than at test time — and charge for it on every build command (`-pl … -am`, and silent staleness when it is forgotten), every IDE import, every version bump across five POMs, and every reader's first five minutes meeting six build files before a line of Java.

   This was the original decision and was reversed before any code was written. It remains the right answer at a size this project does not have.

2. **Two modules — `domain` alone, everything else together** — Keeps compile-time purity for the layer that matters most, at a fifth of the ceremony. Genuinely the best compromise, and the first thing to reach for if this grows. Rejected here because it still imposes a parent POM and `-am` on partial builds, for one boundary out of four.

3. **Single module, no ArchUnit, relying on review** — Rejected outright. Removing the module graph *and* the automated check would leave the architecture as an aspiration. Dropping the stronger mechanism only works if the weaker one is actually present and enforced.

4. **Gradle with source sets per layer** — Comparable enforcement without multi-project overhead. Rejected: the build tool is settled, and switching it to solve a layering problem is the wrong lever.

## Consequences

### Positive
- One `pom.xml`. No parent, no `dependencyManagement` juggling, no drift between modules.
- `mvn verify` means what it says. `-pl … -am` disappears, and with it the failure mode where a partial build silently used stale classes.
- Cloning, importing, and navigating is immediate — a reviewer meets Java, not build files.
- One compile round, one test round, one JaCoCo report with no cross-module merge.
- The layering itself is unchanged and fully intact; only its enforcement mechanism differs.

### Negative
- **The compiler does not enforce the dependency rule.** This is the real cost and should not be softened: nothing stops a developer adding `import org.springframework.stereotype.Component` to a domain class and having it compile. The build still fails — at the ArchUnit stage rather than at `javac` — but the feedback is seconds later and one step further from the edit.
- A single `pom.xml` puts every dependency on every layer's classpath. `domain` can *see* Spring even though it must not use it. Temptation is now a review-and-tooling problem rather than a physical impossibility.
- **ArchUnit becomes load-bearing.** A frozen or deleted rule silently removes the architecture, which is why [`FreezingArchRule` is prohibited](../guides/archunit-governance.md) and why WU-000-D proves each rule against a real violation rather than assuming it works.

### Neutral
- If the service outgrows one module, alternative 2 — extracting `domain` alone — is the cheapest step back toward compile-time enforcement, and this ADR should be superseded rather than edited.
- The ArchUnit suite is a test package, not a module, so it builds and runs with everything else.

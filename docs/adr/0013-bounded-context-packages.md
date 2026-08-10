# ADR-0013: Packages by Bounded Context, Layers Inside Them

**Status**: Accepted
**Date**: 2026-08-10
**Deciders**: David Martinez

## Context

[ADR-0001](0001-clean-architecture-layered-packages.md) settled that the four Clean
Architecture layers are packages in one Maven module, enforced by ArchUnit. It did not
settle what sits at the **top** of the package tree.

The original answer was the layer:

```
com.elatusdev.pokedex
├── domain/          every model, every VO, every port — all of them together
├── application/     every use case
├── infrastructure/  every adapter
└── web/             every controller
```

This is the common shape and it reads well at first. Its problem shows up at around the size
this project reached: `domain/model` held `Pokemon`, its seven child types, `User`, and
`Role` side by side, and `domain/port` held seven ports serving three unrelated concerns.
Nothing in the tree said that `TokenIssuer` and `EvolutionLink` have nothing to do with each
other, or that changing one can never affect the other.

The exercise grades "separation of concerns and **independence of components**". A layered
tree demonstrates the first and is silent on the second: every layer contains everything, so
no component boundary is visible or enforceable. Layering tells you *what kind of thing* a
class is. It does not tell you *what it is about*.

## Decision

**The bounded context is the top-level package. The four layers live inside each one.**

```
com.elatusdev.pokedex
├── catalog/     the upstream read-through view — US01, US02
├── pokedex/     the curated local collection — US03, US04
├── identity/    users, tokens, sessions — WF-AUTH
└── shared/      the shared kernel
    ├── domain/{vo,exception}     ← domain objects only
    ├── port/                     ← technical ports (see below)
    └── web/{error,config}
```

with each context carrying `domain/` · `application/` · `infrastructure/` · `web/`.

**Three contexts, and the boundaries fall where the language changes.**

| Context | Owns | Does not own |
|---|---|---|
| `catalog` | Reading PokeAPI: the fan-out, resilience, the cache, the read use cases for US01 and US02, and the `PokemonCatalog` port they consume | Any local store; any notion of a curator |
| `pokedex` | The `Pokemon` aggregate, replication state, the merge policy, proprietary fields | Anything about who is logged in, beyond a `UserId` |
| `identity` | `User`, `RefreshToken`, hashing, token issuance, sessions | Anything about Pokémon |

**US03 and US04 are one context, not two**, even though they are separate user stories.
Both mutate the same `Pokemon` aggregate, and an aggregate split across two contexts is not
two contexts — it is one aggregate with a boundary drawn through the middle of it, which
loses the invariants the aggregate exists to hold.

**`shared` is a kernel, not a utilities package.** It holds what more than one context
speaks natively: the replicated value objects (`PokemonName`, `Mass`, `Height`, `Category`,
`Description`, `Sprite`, `PokeApiId`) and `InvalidPokemonDataException` under `domain/`, plus
the two technical ports (`CachePort`, `ClockPort`) under `port/`. The rule that keeps it honest is
that **`shared` depends on nothing** — ArchUnit `BC3`. A shared kernel that may depend on a
context re-couples every context through the back door, and the decomposition becomes
decorative.

### Two kinds of port, and only one of them is domain

"Everything the inside needs is a port" is true and it hides a distinction that matters when
deciding where the interface lives.

| | **Domain port** | **Technical port** |
|---|---|---|
| Example | `PokemonRepository`, `PokemonCatalog`, `TokenIssuer`, `PasswordHasher` | `CachePort`, `ClockPort` |
| Signature | Names domain types — `Optional<Pokemon> findById(PokemonId)` | Names none — `Instant now()`, `<T> Optional<T> get(String, Class<T>)` |
| Expresses | A domain capability, in the ubiquitous language | A technical capability, in Java primitives |
| Lives in | `{context}/domain` | `shared/domain` |

A domain port must be inside `domain`: dependency inversion requires the *inner* layer to own
the interface, and moving `PokemonRepository` outward turns the arrow around and takes Clean
Architecture with it.

A technical port has no such claim. `CachePort` is a caching abstraction expressed in `String`
and `Class<T>`; nothing in any `domain` package imports it, and nothing will — its consumers
are `application` and its implementations are `infrastructure`. Filing it under
`shared.domain.port` said "this is a domain object" about something that is not one.

They remain **ports**, not utilities: `L5` asserts they name no framework type, because the
whole point of an abstraction the inside depends on is that the framework stays in the adapter.

The consequence worth stating: after this, `shared/domain` contains only value objects and one
exception. It is now true, rather than approximately true, that everything under a `domain`
package is a domain object.

### Two consequences the full codebase forced

Applying this to the complete implementation — not just the domain core — surfaced two
placements that the smaller tree had not tested.

**One `@RestControllerAdvice` per context, not one globally.** A single `GlobalExceptionHandler`
has to import every context's exceptions to map them, which makes `shared` depend on
`identity`, `catalog` and `pokedex` — a direct `BC3` violation, and exactly the back-door
re-coupling `BC3` exists to prevent. The advice therefore splits along the same seam as
everything else: `AuthExceptionHandler` in `identity`, `CatalogExceptionHandler` in `catalog`,
`PokedexExceptionHandler` in `pokedex` (WU-US04-B), and `ValidationExceptionHandler` in
`shared` for the rows that name no context. Spring supports multiple advices, so this costs
nothing at runtime; the constraint is that **no two advices may claim the same exception
type**, because that is resolved by ordering rather than specificity and the broader one
silently wins.

**`PokemonCatalog` belongs to `catalog`, not `pokedex`.** An earlier draft placed it in
`pokedex/domain/port` on the grounds that the aggregate it returns lives there. That was
premature: once the read use cases existed, the port turned out to be consumed by
`catalog`'s use cases and implemented by `catalog`'s adapter, with `pokedex` never
mentioning it. A port belongs to the context that needs it. It still returns `Pokemon`, so
`catalog` depends on `pokedex.domain` — permitted by `BC4`, which is the rule that lets a
context reach another through its domain and nothing else.

### Packages are created when a class needs one

The contexts were scaffolded with every layer sub-package pre-created and held open by
`.gitkeep`. That was a mistake and is not the convention: **an empty package documents a
prediction, not a design.** Java packages cost nothing to create at the moment a class needs
one, and the work units already say where each class goes. Empty directories left in a
reviewed tree read as over-structure, and `catalog/` in particular reads as abandoned when its
three sub-packages are empty.

The layers keep their existing enforcement. ArchUnit's `..domain..` matches
`catalog.domain`, `pokedex.domain` and `identity.domain` equally, so `L1`–`L4` needed no
change beyond one absolute package list in `L2` that had to become relative.

A new family enforces the contexts themselves:

| Rule | Asserts |
|---|---|
| `BC1` | `identity` depends on neither `catalog` nor `pokedex` |
| `BC2` | `catalog` does not depend on `identity` |
| `BC3` | `shared` depends on no context at all |
| `BC4` | A context reaches another only through its `domain` — never its use cases, adapters, or controllers |
| `L5` | A technical port in `shared/port` names no framework type |
| `CY1` | No cycles **between** contexts |
| `CY2` | No cycles between layers within a context |

`CY1` is new and came free: slicing on `com.elatusdev.pokedex.(*)..` used to slice by layer
and now slices by context.

## Alternatives Considered

1. **Keep layer-first packaging** — Familiar, and the shape most Spring codebases use. Rejected because it makes component independence unobservable and unenforceable: with everything in one `domain`, there is no boundary for a rule to assert. It also scales badly in exactly the direction this project grows — `domain/model` was already holding two unrelated aggregates.

2. **Four contexts, splitting sync from curation** — Maps one-to-one onto the four user stories, which is superficially attractive. Rejected because US03 and US04 write to the same aggregate. Splitting them puts the aggregate's invariants on both sides of a boundary, and the merge policy — the single riskiest rule in the system — would sit in one context while the fields it protects live in another.

3. **Two contexts: `pokedex` and `identity`, catalogue as an outbound adapter** — Less ceremony, and closer to the code as first written. Genuinely defensible, and the honest observation is that the catalogue is *currently* thin: `PokemonCatalog` returns the `Pokemon` aggregate, so `catalog` today holds only exceptions and will hold only adapters. Rejected because that thinness is an artifact of the port's current signature rather than a property of the domain, and because keeping the boundary makes the anticorruption layer a small change later instead of a re-plan. **This is the alternative to revisit if `catalog` never grows a model of its own.**

4. **Flat per-context packages** (`catalog/{model,port,usecase,adapter,web}`) — Shallower and quicker to navigate. Rejected because every `L1`–`L4` rule is written against `..domain..`, `..application..`, `..infrastructure..`, and would have to be rewritten and re-proven against new package names. Paying that to save one directory level is a poor trade.

5. **Separate Maven modules per context** — The strongest enforcement, and the natural end state if this became a real system. Rejected for the same proportionality reason as [ADR-0001](0001-clean-architecture-layered-packages.md): four POMs to express what four ArchUnit rules already express, at this size.

## Consequences

### Positive
- The tree answers "what is this system about?" before "what kind of class is this?". A reader sees three concerns, not four layers.
- Component independence is now **testable**, which is what the grading criterion actually asks for. `BC1`–`BC4` fail the build on a violation.
- Each context is extractable. The dependency direction is already `pokedex → identity → shared`, acyclic, so lifting `identity` into its own service is a package move rather than an untangling.
- Change is localised. Work on US01 touches `catalog/`; work on US04 touches `pokedex/`. Two people can work without meeting in `domain/model`.
- `CY1` came free from the existing slice rule.

### Negative
- **`com.elatusdev.pokedex.pokedex`** is an unfortunate stutter, produced by a context sharing its name with the artifact. Renaming it to `collection` or `curation` would read better; it is left as `pokedex` because that is the word the requirements use, and inventing a synonym costs more comprehension than the stutter does.
- **`catalog` is nearly empty today** and will stay thin while `PokemonCatalog` returns the `pokedex` aggregate. A reviewer may reasonably ask what it is for, and the honest answer is "the boundary is drawn ahead of the model that will fill it" — see alternative 3.
- The tree is deeper. Finding a class requires knowing its context first, and a newcomer may look in the wrong one.
- More directories exist than classes to put in them, which looks like over-structuring until the story work lands.

### Neutral
- `pokedex → identity` is a real, deliberate dependency: `ProprietaryFields.curatedBy` is an `Optional<UserId>`. This is a **shared identifier**, not a shared model — `pokedex` never sees a `User`. If that ever becomes a full `User` reference, the direction should be inverted or the id copied, not the dependency widened.
- If `catalog` grows its own read model, `PokemonCatalog` moves into it and returns a catalogue type, with a translator at the boundary. That is an anticorruption layer, and it is the point at which the third context earns its keep.

## Related

[ADR-0001](0001-clean-architecture-layered-packages.md) (the layers themselves) ·
[package dependencies](../diagrams/package-dependencies.md) ·
[archunit governance](../guides/archunit-governance.md) ·
[WU-000-D](../work-units/WU-000-D-architecture-tests.md)


## Amendment — flat contexts, no `web`

Each context has exactly three directories: `application`, `domain`, `infrastructure`. The
sub-packages that layering produced (`domain/vo`, `domain/port`, `domain/model`,
`domain/exception`, `application/usecase`, `application/result`,
`infrastructure/persistence/model`, …) are gone: 119 files were spread across 55
directories, which is more navigation than the code justifies.

`web` is gone with them. A controller is an inbound adapter and belongs beside the
outbound ones in `infrastructure`; keeping it in its own top-level layer implied the two
were different kinds of thing. Generated contract types moved from `web.api` / `web.dto`
to `contract.api` / `contract.dto`, outside every context, so no `web` package remains
anywhere.

Rules re-pointed rather than dropped: N1 (`..application..`), N2 (`..infrastructure..`),
N3, N4, N5, N6 (`..contract.dto..`), N9, IO2, L1 and L5 all still hold, one segment
shallower. Two needed more than a rename:

- **N9** matched a port by the literal `.port` in its package name. It now requires the
  implemented interface to sit in a project `domain` package.
- **IMF1** scoped immutability to `..domain.vo..`, which no longer exists. It now applies
  to every non-enum, non-interface domain class *without an identity field* — the
  distinction the rule's own rationale draws, since identity is what a value object is
  defined as not having. That exposed `Pokemon` as a mutable aggregate (`addTag` reassigns
  fields); it is skipped because it has identity, not because it was allowlisted, and
  whether it should be immutable is a design question for the sync work units.

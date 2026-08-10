# Design Patterns

The patterns this codebase actually uses, where each one lives, and — more usefully — the
ones we deliberately do not use.

A pattern is a vocabulary, not a goal. If you cannot name the force a pattern resolves,
you do not need the pattern.

---

## Ports and Adapters (Hexagonal)

**Where**: `domain.port` (interfaces) ↔ `infrastructure.*` (implementations)

The domain declares what it needs; infrastructure supplies it. The dependency arrow points
inward at every boundary.

```java
// domain/port/PokemonCatalog.java — owned by the domain
public interface PokemonCatalog {
    Optional<Pokemon> findByPokeApiId(PokeApiId id);
    List<PokemonSummary> fetchPage(int offset, int limit);
}

// infrastructure/pokeapi/PokeApiCatalogAdapter.java — implements it
@Component
public class PokeApiCatalogAdapter implements PokemonCatalog { … }
```

| Port | Adapter | Resolves |
|---|---|---|
| `PokemonRepository` | `JpaPokemonRepositoryAdapter` | Persistence is a detail |
| `PokemonCatalog` | `PokeApiCatalogAdapter` | The upstream API is a detail |
| `CachePort` | `RedisCacheAdapter` | Caching is a detail |
| `TokenIssuer` | `Es256TokenIssuer` | The JWT library is a detail |
| `PasswordHasher` | `BCryptPasswordHasher` | The hashing algorithm is a detail |
| `ClockPort` | `SystemClockAdapter` | **Time is a detail** — this is why no test sleeps |

> **`ClockPort` is the one people skip.** Every staleness rule, TTL, and token expiry
> depends on "now". Injecting the clock is the difference between a test that asserts a
> transition and a test that sleeps for 24 hours.

---

## Policy Object

**Where**: `domain.policy.PokemonMergePolicy`, `domain.policy.StalenessPolicy`

A rule that is too important to bury inside an entity method, and too stateless to deserve
a service. A pure function with a domain name.

```java
public final class PokemonMergePolicy {
    public Pokemon merge(Pokemon existing, Pokemon upstream) {
        return existing
            .withReplicatedFrom(upstream)   // Replicated ← upstream
            .preservingProprietary();       // Proprietary ← existing  (F7)
    }
}
```

**Why not a method on `Pokemon`?** Because the rule is *about* two Pokémon, and belongs to
neither. **Why not a `@Service`?** Because it has no dependencies, needs no Spring, and must
be testable as a property over generated inputs.

---

## Value Object

**Where**: `domain.vo.*` — all records

Wraps a primitive with its invariant, so the invariant cannot be bypassed.

```java
public record Mass(int hectograms) {
    public Mass {
        if (hectograms <= 0) throw new InvalidPokemonDataException("mass must be positive");
    }
    public BigDecimal toKilograms() {
        return BigDecimal.valueOf(hectograms).divide(BigDecimal.TEN);
    }
}
```

Three things this buys, all of which a bare `int` loses:

1. `Mass` cannot be negative anywhere in the system, ever.
2. The hectogram-to-kilogram conversion exists once. Nobody divides by ten at a call site.
3. `void setMass(Mass m)` cannot receive a height by mistake.

> **The unit bug this prevents**: PokeAPI reports weight in hectograms and height in
> decimetres. Bulbasaur is `weight: 69`. Without the value object, someone renders "69 kg"
> — and someone else, elsewhere, divides by ten twice.

---

## Aggregate

**Where**: `Pokemon`, `User`

One root per cluster; external references are id-only; the aggregate is the transaction and
consistency boundary.

```java
// yes — id reference across aggregates
public record Pokemon(PokemonId id, /* … */ UserId curatedBy) { }

// no — object reference drags User into every Pokemon load
public record Pokemon(PokemonId id, /* … */ User curatedBy) { }
```

The rule that follows: **never modify two aggregates in one transaction.** If you need to,
you have either drawn the boundary wrong or you need a domain event.

---

## Domain Event

**Where**: `PokemonReplicated`, `PokemonCustomized`, `PokemonDeleted`, `RefreshTokenReuseDetected`

For side effects that are not part of the aggregate's own consistency: cache eviction, audit
logging.

All are async **except `RefreshTokenReuseDetected`** — a stolen token must not survive the
request that detected it, so family revocation is synchronous. When you find yourself
arguing about async versus sync for an event, that argument is about whether the consequence
is part of the security boundary.

---

## Adapter-side Mapper

**Where**: `infrastructure.pokeapi.PokeApiMapper`, `infrastructure.persistence.PokemonDataModelMapper`

Translation lives at the edge that owns the foreign shape. The domain never sees a
`PokeApiPokemonResponse` or a `PokemonDataModel`.

The mapper is also where every upstream quirk is absorbed once: unit conversion, control-character
stripping, English-language filtering, recursive tree flattening. A quirk handled in two places
is a quirk that will be handled inconsistently.

---

## Patterns we deliberately do not use

| Pattern | Why not |
|---|---|
| **Repository returning `Optional<T>` everywhere plus a separate `exists()`** | One query, one round trip. `findById().isPresent()` beats `exists()` followed by `findById()` |
| **Generic `AbstractCrudService<T>`** | Saves twenty lines and costs every reader an inheritance chase. One use case class per operation instead |
| **`@Service` wrapping a single repository call** | A pass-through layer. If the use case has no rule, it should call the port directly |
| **Builder on domain records** | Records already have a canonical constructor. Builders reintroduce the "half-constructed object" state that records exist to eliminate |
| **Specification pattern for queries** | Spring Data derived queries and `@Query` cover our needs. Specifications earn their place at a complexity we have not reached |
| **Event sourcing** | The audit requirement is a log, not a rebuildable history |
| **CQRS with separate read models** | We have command/query separation *at the class level*, which is the part that pays. Separate stores would be ceremony |
| **Anaemic domain + fat service** | The failure mode this codebase is organised to prevent |

---

## Related

[Java patterns](java-patterns.md) · [Error handling](error-handling.md) · [Element relationships](../diagrams/element-relationships.md)

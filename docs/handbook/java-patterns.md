# Java Patterns

Language-level idioms for Java 24. What to reach for, what to avoid, and why.

---

## Records for immutable data — the default

Every value object is a record. Every command, result, and mapper input is a record. A class
is justified only when it carries behaviour beyond its data.

```java
public record PokemonSummary(
    PokeApiId pokeApiId, PokemonName name, Category category,
    Mass mass, Sprite sprite, List<Ability> abilities
) {
    public PokemonSummary {
        abilities = List.copyOf(abilities);   // defensive copy in the compact constructor
    }
}
```

> **The `List.copyOf` in the compact constructor is not optional.** A record is only
> immutable if its components are. Without it, the caller keeps a mutable handle on your
> internals — and the compiler will not warn you.

Enforced by `java:S6206`. Related: `java:S6207` (no redundant record members), `java:S6218`
(override `equals` when a component is an array — better still, do not use arrays).

## Validate in the compact constructor

```java
public record PokemonName(String value) {
    public PokemonName {
        Objects.requireNonNull(value, "name");
        var trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 60) {
            throw new InvalidPokemonDataException("name must be 1..60 characters");
        }
        value = trimmed;   // normalise here, once
    }
}
```

An invalid instance cannot exist. That is the entire point — every downstream method is
relieved of checking.

## Optional at return boundaries, never as a field or parameter

```java
Optional<Pokemon> findByPokeApiId(PokeApiId id);        // yes
List<Pokemon> findAllByRegion(Region region);           // yes — empty list, never null
void update(Optional<Region> region);                   // no — overload or use a command object
private Optional<String> notes;                         // no — not serialisable, not a field type
```

- Never return `null` for absence (`java:S2789`).
- Never return `null` for an empty collection (`java:S1168`).
- Never call `get()` without `isPresent()` (`java:S3655`) — prefer `orElseThrow`, `map`, `flatMap`.

```java
return repository.findByPokeApiId(id)
    .map(mapper::toDetail)
    .orElseThrow(() -> new PokemonNotFoundException(id));
```

## Sealed interfaces with exhaustive switch

For closed sets, the compiler checks exhaustiveness so a new variant becomes a compile error
rather than a silent fall-through.

```java
public sealed interface SyncOutcome permits Synced, Skipped, Failed { }

var message = switch (outcome) {
    case Synced s   -> "synced " + s.pokeApiId();
    case Skipped sk -> "skipped: " + sk.reason();
    case Failed f   -> "failed: " + f.cause();
};   // no default — adding a variant breaks the build, which is what you want
```

Prefer this to `instanceof` chains and to the Visitor pattern. Related: `java:S6201`,
`java:S6878`, `java:S6880`.

## Exceptions: one throw, specific type, translated at the boundary

```java
// domain — specific, meaningful, carries context
public class PokemonNotFoundException extends RuntimeException {
    private final transient PokemonId id;
    public PokemonNotFoundException(PokemonId id) {
        super("No Pokemon with id " + id.value());
        this.id = id;
    }
}
```

- **One `throw` per method.** Multiple exits from one method usually means it does two things.
- **Never catch `Exception` or `Throwable`** (`B-05`). Catch the specific type.
- **No empty catch blocks** (`B-06`). Log, rethrow, or wrap — pick one.
- **Log *or* rethrow, never both** (`java:S2139`) — double logging makes an incident twice as hard to read.
- Domain exceptions extend `RuntimeException` and live in `domain.exception`.

See [error handling](error-handling.md) for the translation layer.

## Text blocks for anything multi-line

```java
var sql = """
    SELECT p.* FROM pokemon p
    WHERE p.region = :region
      AND p.replication_state <> 'FAILED'
    """;
```

Never concatenate multi-line strings with `+` (`java:S6126`).

## Dates and time

```java
LocalDate.of(2026, Month.AUGUST, 9);       // yes
LocalDate.of(2026, 8, 9);                   // no — java:S8694

Instant.now(clock);                         // yes — ClockPort injected
Instant.now();                              // no — java:S8688, and untestable
```

`java.time.Month`, never a bare int. `.now()` always takes a `Clock` or `ZoneId`. This is
what makes staleness and expiry testable without sleeping.

## Immutability and defensive copying

```java
public List<Tag> tags() { return List.copyOf(tags); }        // yes
public List<Tag> tags() { return tags; }                     // no — hands out your internals
public List<Tag> tags() { return Collections.unmodifiableList(tags); }  // weaker — a view, still backed by yours
```

Favour immutability unless there is a reason not to. Collections returned from domain
objects are always copies.

## No Javadoc

**Do not write Javadoc.** Not on public API, not on domain classes, not "just a one-liner".

```java
/**
 * Merges the upstream Pokemon into the existing one.
 *
 * @param existing the existing Pokemon
 * @param upstream the upstream Pokemon
 * @return the merged Pokemon
 */
public Pokemon merge(Pokemon existing, Pokemon upstream) { … }
```

That comment says nothing the signature does not. It costs four lines, it will not be
updated when the behaviour changes, and a reader who trusts it will be wrong. Comments that
restate the code are worse than no comments, because they carry authority they have not
earned.

### What carries the meaning instead

| Question | Where it is answered |
|---|---|
| What does this do? | The method name and its signature. If they do not answer it, rename — do not annotate |
| How do I call it? | The test. Tests are executable examples that cannot go stale |
| Why does it exist? | An [ADR](../adr/0009-no-bundled-client.md). Rationale belongs where it is versioned and reviewed |
| What are the rules? | This handbook |

### The narrow exception: a `//` comment explaining *why*

A short line comment is justified when it captures a **non-obvious reason** that cannot be
expressed in code:

```java
// hectograms, not kilograms — PokeAPI's unit, converted in Mass
private final int massHectograms;

// synchronous, deliberately: a stolen token must not survive the request that detected it
revokeFamily(token.familyId());
```

Both explain something the reader cannot deduce. Neither restates the code. That is the test
to apply: **if deleting the comment loses no information, it was noise.**

### Exclusions

Generated sources carry Javadoc from the generator. They are excluded from this rule, as they
are from coverage and the copyright check — they are not our code.

### Enforcement

A grep target in `make verify` fails on `/**` in `src/main/java` outside generated sources.
Without it this rule is a preference, and preferences lose to habit.

```bash
! grep -rn --include=*.java '/\*\*' src/main/java 2>/dev/null
```

---

## Naming and size

`UpperCamel` types, `lowerCamel` members, `CONSTANT_CASE` constants. Method body ≤ 20 lines,
class ≤ 300 (500 for entities, config, controllers), cyclomatic complexity ≤ 10, nesting ≤ 3
— use guard clauses and early returns.

IDs are `Long`. The one documented exception is `pokeApiId`, an upstream identifier we do
not own.

## Things that are available and still wrong here

| Feature | Why not |
|---|---|
| **Structured concurrency** | Preview at language level 24. Banned — see [ADR-0004](../adr/0004-java-24-language-level.md) |
| `var` for non-obvious types | `var result = service.process()` tells a reader nothing. Use it where the right-hand side names the type |
| Checked exceptions in the domain | They leak infrastructure concerns into signatures the domain owns |
| `Stream` as a return type | Prefer `Collection` — a stream can only be consumed once, and callers forget |
| Lombok `@Data` | Generates mutable setters and a fragile `equals`. `@Builder` and `@Getter` are sanctioned; `@Data` is not |

## Related

[Stream API](stream-api.md) · [Design patterns](design-patterns.md) · [Concurrency](concurrency.md)

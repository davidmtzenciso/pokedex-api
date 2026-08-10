# Stream API

Streams are for expressing a transformation, not for proving you know streams. A `for` loop
that reads clearly beats a stream that does not.

---

## The rules

| Rule | Why |
|---|---|
| **Side-effect-free** | No mutation of external state inside `map`, `filter`, or `peek`. A stream that mutates is a loop wearing a costume |
| **`toList()`, not `collect(toUnmodifiableList())`** | `java:S6204`. Shorter, and immutable by default since 16 |
| **Return `Collection`, not `Stream`** | A stream is single-use. A caller who iterates twice gets `IllegalStateException` at runtime |
| **No `peek` for logging** | It is a debugging hook with no guaranteed execution. Log before or after the pipeline |
| **One pipeline, one purpose** | If you need a comment to explain a pipeline, split it or use a loop |
| **Never `parallelStream()` casually** | It uses the common ForkJoin pool. On blocking work it starves everything else — see [concurrency](concurrency.md) |

---

## Patterns we use

**Mapping a collection** — the overwhelmingly common case.

```java
public List<PokemonSummary> toSummaries(List<Pokemon> pokemon) {
    return pokemon.stream()
        .map(this::toSummary)
        .toList();
}
```

**Filtering to the English entry** — PokeAPI arrays are multilingual, and taking `[0]` gives
you Japanese.

```java
private Category extractCategory(SpeciesResponse species) {
    return species.genera().stream()
        .filter(g -> "en".equals(g.language().name()))
        .findFirst()
        .map(g -> new Category(g.genus()))
        .orElseThrow(() -> new UpstreamMappingException("no English genus"));
}
```

**Grouping** — when the result is genuinely a map.

```java
Map<Region, List<Pokemon>> byRegion = pokemon.stream()
    .collect(Collectors.groupingBy(Pokemon::region));
```

**Flattening a recursive tree** — the evolution chain. Note that the recursion is a plain
method; the stream only handles the fan-out at each level.

```java
private List<EvolutionLink> flatten(ChainNode node) {
    return node.evolvesTo().stream()
        .mapMulti((child, sink) -> {
            sink.accept(new EvolutionLink(node.speciesId(), child.speciesId(), child.trigger()));
            flatten(child).forEach(sink);
        })
        .toList();
}
```

**Partitioning a batch result** into the three outcomes the summary reports.

```java
var byOutcome = results.stream().collect(Collectors.groupingBy(SyncResult::outcome));
var summary = new BatchSummary(
    byOutcome.getOrDefault(SUCCEEDED, List.of()).size(),
    byOutcome.getOrDefault(FAILED, List.of()).size(),
    byOutcome.getOrDefault(SKIPPED, List.of()).size());
```

---

## Anti-patterns, with the fix

**Mutating from inside a stream**

```java
// no — this is a for loop pretending
var names = new ArrayList<String>();
pokemon.stream().forEach(p -> names.add(p.name().value()));

// yes
var names = pokemon.stream().map(p -> p.name().value()).toList();
```

**A stream over three elements**

```java
// no — harder to read than the thing it replaced
return Stream.of(a, b, c).filter(Objects::nonNull).findFirst().orElse(fallback);

// yes
if (a != null) return a;
if (b != null) return b;
return c != null ? c : fallback;
```

**A database query inside a `map`** — this is the N+1 problem with better syntax.

```java
// no — one query per element
return ids.stream().map(repository::findById).flatMap(Optional::stream).toList();

// yes — one query
return repository.findAllById(ids);
```

**`Optional.get()` after `filter`**

```java
// no — java:S3655
var first = list.stream().filter(pred).findFirst().get();

// yes
var first = list.stream().filter(pred).findFirst()
    .orElseThrow(() -> new PokemonNotFoundException(id));
```

**Nested streams three levels deep** — extract the inner pipeline into a named method. The
name is the documentation the nesting was missing.

---

## When not to use a stream at all

- You need the index. Use a loop, or `IntStream.range` only if the index is genuinely part of the logic.
- You need to break early on a condition that is not `findFirst` / `anyMatch`.
- The body throws a checked exception. Wrapping it in a lambda produces noise that hides the intent.
- The loop has one statement in it. `for (var p : pokemon) save(p);` is fine.

## Related

[Java patterns](java-patterns.md) · [Concurrency](concurrency.md) · [Persistence patterns](persistence-patterns.md)

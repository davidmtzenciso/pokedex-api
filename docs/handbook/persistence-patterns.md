# Persistence Patterns

JPA lives entirely inside `infrastructure`. The domain does not know it exists.

---

## Three shapes, never confused

| Shape | Lives in | Purpose |
|---|---|---|
| `Pokemon` (domain) | `domain.model` | Business rules. No annotations, no framework |
| `PokemonDataModel` | `infrastructure.persistence.model` | JPA entity. **Data only** — no behaviour |
| `PokemonDetailDTO` | generated | The wire format |

A mapper translates at each boundary. The domain object never leaks a JPA proxy, and the
data model never leaves infrastructure (ArchUnit `N4`, `IO1`).

> **Why not use the JPA entity as the domain object?** Because then every invariant lives
> next to a no-arg constructor and mutable setters that Hibernate requires, lazy loading
> makes `equals` unpredictable, and the domain becomes untestable without a persistence
> context. The mapping cost is real and it is worth paying.

## The port

```java
// domain/port/PokemonRepository.java — the domain owns this interface
public interface PokemonRepository {
    Optional<Pokemon> findById(PokemonId id);
    Optional<Pokemon> findByPokeApiId(PokeApiId id);
    Page<Pokemon> findAll(PokemonFilter filter, Pageable pageable);
    Pokemon save(Pokemon pokemon);
    void delete(PokemonId id);
}
```

Domain types in, domain types out. `Pageable` is the one Spring type that crosses, and it is
a deliberate, documented exception — reimplementing pagination to stay pure would be
ceremony.

## Deletes are hard

```java
@Entity
@Table(name = "pokemon")
public class PokemonDataModel {
    @OneToMany(mappedBy = "pokemon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PokemonAbilityDataModel> abilities = new ArrayList<>();
}
```

`DELETE` removes the row and, by cascade and orphan removal, every child with it. There is
no `deletedAt`, no `@SQLRestriction`, and no tombstone.

> **Why not soft delete?** Nothing here needs recoverable deletes or audit history. Soft
> delete would buy those at the cost of a `deleted_at IS NULL` predicate on **every** query,
> a partial-index clause on every unique constraint, and a class of bug where one forgotten
> filter resurrects deleted data. Getting a Pokémon back is a re-sync, which takes one
> request and produces a genuinely new record.
>
> If auditing were ever required, the answer is an append-only audit log — not a nullable
> column that quietly changes the meaning of every `SELECT` in the codebase. See
> [ADR-0010](../adr/0010-hard-deletes.md), which also names the real cost: deleting a record
> destroys its proprietary fields permanently, because those are the one thing re-sync
> cannot bring back.

## Optimistic locking

```java
@Version
private Long version;
```

A concurrent edit produces `OptimisticLockingFailureException` → **412 STALE_VERSION**, and
the client is told to reload. The alternative is a silent lost update, which is a data-loss
bug that nobody notices for months.

Pessimistic locking is not used. It would serialise curators editing different fields of the
same record for no benefit.

## Flyway

```
V1__schema.sql          tables, indexes, constraints
V2__seed.sql            the original 151 + demo users
V3__add_notes.sql       every change is a new file
```

> **Never edit an applied migration.** Flyway fails fast on a checksum mismatch — which is
> correct behaviour and saves you from a schema that differs between environments. Add a
> corrective migration instead. In dev, `docker compose down -v` is the reset.

The partial unique index is worth noting, because a plain unique constraint would be wrong:

```sql
CREATE UNIQUE INDEX ux_pokemon_poke_api_id
    ON pokemon (poke_api_id) WHERE poke_api_id IS NOT NULL;
```

Still partial, but for one reason only: `DRAFT` records have no `pokeApiId`, and a plain
unique constraint would let at most one of them exist. With hard deletes there is no
second clause to remember.

## Avoiding N+1

```java
// no — one query per Pokemon for its abilities
var pokemon = repository.findAll(pageable);
pokemon.forEach(p -> p.getAbilities().size());

// yes — one query, entity graph declared at the repository
@EntityGraph(attributePaths = {"abilities", "stats", "types", "tags"})
Page<PokemonDataModel> findAll(Pageable pageable);
```

Enable `spring.jpa.properties.hibernate.generate_statistics` in dev. A page of 10 should
issue **one** query. If it issues 41, you have an N+1 and the statistics will say so before
a reviewer does.

## Query style

| Situation | Approach |
|---|---|
| Simple lookup | Derived query — `findByPokeApiId(Integer id)` |
| Multi-criteria filter | `Specification`, or a `@Query` with optional predicates |
| Projection for a list view | An interface projection — do not load the full aggregate to render a row |
| Anything needing native SQL | Confine it to `infrastructure`, and justify why JPQL was insufficient |

Never string-concatenate a query. Parameterised always (OWASP A03).

## Transactions

The use case is the boundary. Repositories are never `@Transactional` themselves — that
would make each call its own transaction and quietly break atomicity across a use case that
does two writes.

No remote I/O inside a transaction. Fetch from upstream first, then open the transaction to
persist.

## Testing

- **Component tests use Testcontainers Postgres**, not H2. H2's dialect differs enough that a passing H2 test can hide a broken production query — partial indexes in particular behave differently.
- Each test starts from a migrated, seeded schema; the container is reused across the class.
- Assert on the **database state**, not only the return value, when testing a write.

## Related

[Spring patterns](spring-patterns.md) · [Entity relationships](../diagrams/entity-relationship.md) · [Testing pyramid](testing-pyramid.md)

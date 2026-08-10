# Sequence — Listing a Page (the 1 + 2N problem)

The read that the upstream API makes hard, and the reason caching is load-bearing rather
than decorative.

```mermaid
sequenceDiagram
    autonumber
    participant CL as API client
    participant Ctrl as PokemonController<br/>implements PokemonApi
    participant UC as ListPokemonUseCase<br/>(framework-free)
    participant Cache as CachePort<br/>(Redis adapter)
    participant Cat as PokemonCatalog<br/>(PokeAPI adapter)
    participant API as PokeAPI v2

    CL->>Ctrl: GET /api/v1/pokedex/pokemon?page=0&size=10
    Note over Ctrl: public route - no principal required
    Ctrl->>UC: list(PageRequest(0, 10)) - default size
    UC->>Cache: get("pokeapi:page:0:10")

    alt cache hit - the normal case
        Cache-->>UC: PokemonSummary[10]
        Note over UC,Cache: 1 Redis read, sub-millisecond
    else cache miss - cold path
        Cache-->>UC: empty
        UC->>Cat: fetchPage(offset=0, limit=10)
        Cat->>API: GET /pokemon?limit=10&offset=0
        API-->>Cat: 10 x {name, url} only
        Note over Cat,API: 2N calls on virtual threads,<br/>Semaphore(16) bounded
        par concurrently, per Pokémon
            Cat->>API: GET /pokemon/{id}
            API-->>Cat: sprite, weight, abilities, stats
        and
            Cat->>API: GET /pokemon-species/{id}
            API-->>Cat: genera[] -> category, names[], flavor_text[]
        end
        Cat-->>UC: PokemonSummary[10]
        UC->>Cache: put(key, TTL 24h)
    end

    UC-->>Ctrl: Page<PokemonSummary>
    Ctrl-->>CL: 200 PokemonPageDTO

    alt upstream down, local replica exists
        UC->>UC: fall back to the local read model
        Ctrl-->>CL: 200 with stale=true
    else upstream down, nothing local
        Ctrl-->>CL: 502 ProblemDetail UPSTREAM_UNAVAILABLE
    end
```

## The arithmetic

`GET /api/v2/pokemon?limit=N` returns **only** `{name, url}`. The sprite, mass, and
abilities need `/pokemon/{id}` per row; the category (`genera[]` filtered to English) needs
`/pokemon-species/{id}` per row.

**1 + 2N upstream calls for one page.**

| Page size | Calls | Sequential at ~80 ms |
|---:|---:|---|
| 10 (default) | 21 | ~1.7 s |
| 100 (maximum) | 201 | ~16 s |

The cap at 100 is not arbitrary — it bounds the damage a single request can do to a free,
fair-use-limited public API. The default of 10 keeps the common case cheap.

## What it encodes

- **The use case knows nothing about Redis or HTTP.** It talks to `CachePort` and `PokemonCatalog`. Both are interfaces the domain owns.
- **The fan-out is bounded twice.** `Semaphore(16)` caps in-flight requests, and the page-size cap of 100 bounds how many a single request can queue. Unbounded on either axis gets us rate-limited mid-demo.
- **Virtual threads, not reactive.** 40 blocking calls are cheap on virtual threads, and the stack trace stays readable. WebFlux would bridge back to a pool at the JPA boundary anyway.
- **Upstream failure has two answers, and neither is 500.** With a local replica: 200 plus `stale: true`. Without: 502. A 500 would blame us for someone else's outage.

## Related

[Data flow — sync](data-flow-sync.md) · [ADR-0006](../adr/0006-redis-cache-pokeapi-fanout.md)

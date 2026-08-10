# Entity Relationships

The relational model. Cardinality, keys, and the columns that carry meaning.

```mermaid
erDiagram
    USER ||--o{ POKEMON : "curated by"
    USER ||--o{ REFRESH_TOKEN : "holds"
    POKEMON ||--o{ POKEMON_ABILITY : "has"
    POKEMON ||--o{ POKEMON_STAT : "has"
    POKEMON ||--o{ POKEMON_TYPE : "has"
    POKEMON ||--o{ POKEMON_TAG : "annotated with"
    POKEMON ||--o{ LOCALIZED_NAME : "named in"
    POKEMON ||--o{ EVOLUTION_LINK : "evolves via"

    USER {
        Long id PK
        String username UK
        String email UK
        String passwordHash
        String roles
        Instant createdAt
    }
    POKEMON {
        Long id PK
        Integer pokeApiId UK
        String name
        String category
        Integer massHectograms
        Integer heightDecimetres
        Integer baseExperience
        String spriteUrl
        String description
        String region
        String notes
        ReplicationState replicationState
        Long curatedBy FK
        Instant syncedAt
        Long version
        Instant createdAt
        Instant updatedAt
    }
    POKEMON_ABILITY {
        Long id PK
        Long pokemonId FK
        String name
        Integer slot
        Boolean hidden
    }
    POKEMON_STAT {
        Long id PK
        Long pokemonId FK
        String name
        Integer baseValue
        Integer effort
    }
    POKEMON_TYPE {
        Long id PK
        Long pokemonId FK
        String name
        Integer slot
    }
    POKEMON_TAG {
        Long id PK
        Long pokemonId FK
        String label
    }
    LOCALIZED_NAME {
        Long id PK
        Long pokemonId FK
        String locale
        String value
        NameSource source
    }
    EVOLUTION_LINK {
        Long id PK
        Long pokemonId FK
        Integer fromPokeApiId
        Integer toPokeApiId
        String trigger
        Integer minLevel
    }
    REFRESH_TOKEN {
        Long id PK
        Long userId FK
        String familyId
        String jti UK
        Instant expiresAt
        Instant revokedAt
    }
```

## Columns that are doing real work

| Column | Why it exists |
|---|---|
| `pokemon.pokeApiId` **nullable** | A `DRAFT` record was created by hand and has no upstream counterpart yet. The unique index is partial: `WHERE poke_api_id IS NOT NULL` |
| `massHectograms`, `heightDecimetres` | Upstream units, stored raw. Conversion happens once, in the `Mass` / `Height` value objects — never at a call site |
| `localized_name.source` | `UPSTREAM` or `CURATOR`. This single discriminator is what lets re-sync replace upstream names while preserving curator overrides |
| `pokemon.version` | Optimistic locking. A concurrent edit gets 412, not a silent last-write-wins |
| `pokemon.updatedAt` | Auditing only. **Deletes are hard** — there is no `deletedAt`, no recoverable state, and no filter on every query |
| `refresh_token.familyId` | Groups all tokens descended from one login, so replaying a rotated token can revoke the whole family |

Both top-level tables satisfy the brief's requirement of a unique primary key plus well
beyond the minimum of two descriptive attributes.

## Related

[Domain aggregates](domain-aggregates.md) · [Replication state machine](replication-state-machine.md) · [Refresh token rotation](refresh-token-rotation.md)

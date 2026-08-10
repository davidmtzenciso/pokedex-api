# Sequence — Re-sync Against a Customised Record

The write path that must not destroy curator work.

```mermaid
sequenceDiagram
    autonumber
    participant CL as API client
    participant JRF as JwtRequestFilter
    participant Sess as Redis jti session
    participant Ctrl as SyncController
    participant UC as ReSyncPokemonUseCase
    participant Merge as PokemonMergePolicy<br/>(pure domain)
    participant Repo as PokemonRepository
    participant DB as PostgreSQL

    CL->>JRF: POST /api/v1/pokedex/sync/1 + Bearer JWT
    JRF->>JRF: verify ES256 by kid, reject alg:none
    JRF->>Sess: is jti still live?

    alt session revoked
        Sess-->>JRF: no
        JRF-->>CL: 401 TOKEN_REVOKED
    else session live
        Sess-->>JRF: yes
        JRF->>Ctrl: authenticated principal
        Ctrl->>UC: reSync(pokeApiId=1)
        UC->>Repo: findByPokeApiId(1)
        Repo->>DB: SELECT ... WHERE poke_api_id = 1
        DB-->>Repo: state=STALE, region=KANTO, tags=[starter]
        Repo-->>UC: existing aggregate
        UC->>Merge: merge(existing, upstream)
        Merge->>Merge: Replicated <- upstream
        Merge->>Merge: Proprietary <- existing (F7)
        Merge->>Merge: state' = CUSTOMIZED
        Merge-->>UC: merged aggregate
        UC->>Repo: save(merged)
        Repo->>DB: UPDATE ... SET version = version + 1
        Ctrl-->>CL: 200 PokemonDetailDTO
    end
```

## What it encodes

- **`PokemonMergePolicy` is a pure function in `domain`.** No repository, no clock, no Spring. That is what makes F7 — "re-sync preserves every proprietary field" — testable as a **property** over generated field combinations rather than as an integration scenario.
- **The merge takes proprietary fields from the *existing record*, never from upstream.** Steps 12–13 are the whole decision, drawn.
- **Auth is two checks, not one.** A valid ES256 signature is not sufficient; the `jti` session must still be live. That is what makes logout real.
- **`version` increments**, so a concurrent curator edit gets 412 rather than silently losing.

## Related

[Replication state machine](replication-state-machine.md) · [Re-sync error paths](error-paths-resync.md) · [ADR-0007](../adr/0007-proprietary-field-merge-policy.md)

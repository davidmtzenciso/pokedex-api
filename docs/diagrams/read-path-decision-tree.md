# Read Path Decision Tree

Which source answers a read, and what happens when upstream is down. The executor follows
this tree — no ambiguity, no stopping to ask.

`[Rectangle]` = action · `{Diamond}` = decision · `([Rounded])` = terminal

```mermaid
flowchart TD
    A[Request for a Pokémon arrives] --> B{Local replica exists?}
    B -->|no| C{Read-only browse<br/>or explicit sync?}
    C -->|browse| D[Serve from PokeAPI<br/>via Redis cache]
    C -->|sync| E[Fetch upstream, map,<br/>persist as SYNCED]
    B -->|yes| F{syncedAt + TTL<br/>older than now?}
    F -->|no| G[Serve local replica]
    F -->|yes| H{Record has<br/>proprietary edits?}
    H -->|no| I[Re-sync: overwrite<br/>replicated fields]
    H -->|yes| J[Re-sync: merge, preserve<br/>every proprietary field]
    D --> K{Upstream reachable?}
    E --> K
    I --> K
    J --> K
    K -->|yes| L[200 with fresh data]
    K -->|no, local copy exists| M[200 with stale:true flag]
    K -->|no, no local copy| N([502 ProblemDetail<br/>upstream-unavailable])
    G --> L
```

## What it encodes

- **Every decision node is exhaustive.** No unhandled case, no dangling branch.
- **`H` is the merge decision.** The two outcomes differ only in whether proprietary fields survive, and they always do when present.
- **`K` has three outcomes, and 500 is not one of them.** An upstream outage produces a 502 or a flagged-stale 200 — never an error attributed to us.
- **`stale: true` is a success response.** The client renders a subtle badge, not an error page.

## Related

[Sequence — listing a page](sequence-list-page.md) · [Replication state machine](replication-state-machine.md)

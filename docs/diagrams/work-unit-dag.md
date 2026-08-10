# Work Unit DAG

Execution order across all seven workflows, constructed by **backward induction** from the
acceptance criteria rather than forward from "foundation first". Nodes at the same level
with no edge between them may be built in any order or in parallel.

Driven by the [execution prompt](../prompts/pokedex-api-prompt.md), which groups these into
seven phases — one per workflow.

```mermaid
graph TD
    subgraph wf000["WF-000 Foundation"]
        A["WU-000-A · project + gates"]
        B["WU-000-B · contract"]
        C["WU-000-C · domain core"]
        D["WU-000-D · ArchUnit"]
    end
    subgraph wfauth["WF-AUTH"]
        AA["WU-AUTH-A · user domain"]
        AB["WU-AUTH-B · security adapters"]
        AC["WU-AUTH-C · endpoints + route policy"]
    end
    subgraph wf01["WF-US01 Enumeration"]
        A1["WU-US01-A · catalog adapter"]
        B1["WU-US01-B · cache"]
        C1["WU-US01-C · list endpoint"]
    end
    subgraph wf02["WF-US02 Detail"]
        A2["WU-US02-A · detail mapping"]
        B2["WU-US02-B · detail endpoint"]
    end
    subgraph wf03["WF-US03 Sync"]
        A3["WU-US03-A · persistence"]
        B3["WU-US03-B · merge + sync use cases"]
        C3["WU-US03-C · endpoints + seed"]
    end
    subgraph wf04["WF-US04 Local CRUD"]
        A4["WU-US04-A · CRUD use cases"]
        B4["WU-US04-B · CRUD endpoints"]
    end
    subgraph wf999["WF-999 Delivery"]
        A9["WU-999-A · containerisation"]
        B9["WU-999-B · verification"]
    end

    A --> B
    A --> C
    A --> D
    C --> AA --> AB --> AC
    C --> A1
    C --> B1
    C --> A3
    B --> C1
    A1 --> C1
    B1 --> C1
    A1 --> A2 --> B2
    C1 --> B2
    A1 --> B3
    A3 --> B3 --> C3
    AC --> C3
    A3 --> A4 --> B4
    AC --> B4
    C3 --> A4
    B4 --> A9
    B2 --> A9
    C3 --> A9
    D --> B9
    A9 --> B9

    style A fill:#e1f5fe
    style B fill:#fff3e0
    style C fill:#fff3e0
    style D fill:#c8e6c9
    style B9 fill:#c8e6c9

    %% Parallel: WU-AUTH-A, US01-A, US01-B, US03-A are independent once WU-000-C lands
    %% WU-000-D depends only on WU-000-A — build it early
    %% Critical path: 000-A → 000-C → US03-A → US03-B → US03-C → US04-A → US04-B → 999-A → 999-B
```

## What the shape tells you

**WU-000-C is the widest fan-out.** Once the ports are declared, four tracks proceed
independently — auth, the catalog adapter, the cache, and persistence. That is the payoff of
declaring ports before implementing anything.

**WU-000-D depends only on WU-000-A.** Build the ArchUnit suite early, not last. Rules that
exist before the code they govern fail on the first violation rather than during a cleanup
pass at the end.

**US03 sits on the critical path**, not US01. Persistence, the merge policy, and the sync
endpoints are what US04 needs before it can modify anything — and the merge policy is the
riskiest thing in the build.

**WU-US03-B has two parents, and the second is easy to miss.** It needs WU-US01-A as well as
WU-US03-A: a sync use case reads from `PokemonCatalog`, and the only implementation of that
port is the PokeAPI adapter built in US01. The port alone is enough to write the use case
against a mock, but not enough to prove it works.

**WF-AUTH blocks the mutating endpoints in US03 and US04**, which is why it is not deferred
to the end despite not being a numbered story.

**WU-000-B sits ahead of every controller**, and that is a topological fact rather than a
preference. No `@RestController` exists that is not an override of a generated `*Api`
method, so the contract must compile before any endpoint work starts. It also has consumers
outside this graph entirely: anything calling this API generates its own types from that
document, so the contract can be published and consumed before a single controller exists
([ADR-0008](../adr/0008-openapi-contract-distribution.md)).

## Related

[Work units](../work-units) · [Execution prompt](../prompts/pokedex-api-prompt.md)

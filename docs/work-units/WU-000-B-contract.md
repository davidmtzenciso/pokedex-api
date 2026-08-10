# WU-000-B — Contract

| Field | Value |
|---|---|
| **Work Unit** | WU-000-B |
| **Parent** | [WF-000 Foundation](../workflows/WF-000-foundation.md) |
| **Objective contribution** | The OpenAPI document, and Java interfaces generated from it |
| **Estimate** | M |
| **Status** | done |

## Objective

Author the API contract by hand and make it generate compiling Java interfaces and DTOs.
On the critical path: no controller work begins until this exits green.

## Entry Criteria

- WU-000-A green

## Inputs

| Input | Source | Used by |
|---|---|---|
| 15 operations | [WF-000 §3.2](../workflows/WF-000-foundation.md) — **authored during this work unit; the section was referenced but missing** | B1 |
| Error envelope | [error handling](../handbook/error-handling.md) | B2 |
| Pagination rules | [WF-000 §3.2](../workflows/WF-000-foundation.md) | B1 |

## Outputs

- `src/main/resources/openapi/pokedex-api.yaml`
- Generated `*Api` interfaces and `*DTO` models that compile

---

## ▶ Activity Sequence

### B1 — Author the OpenAPI document

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `src/main/resources/openapi/pokedex-api.yaml` |
| **Intent** | The contract is authored, not derived. It is the source of truth |
| **Depends on** | — (entry) |

**How**
OpenAPI 3.1. All 15 operations from workflow §3.2. Paths exclude the `/api` context path.
Every list operation declares `page` (default `0`, minimum `0`) and `size` (**default 10,
minimum 1, maximum 100**). Validation constraints — `minimum`, `maxLength`, `pattern`,
`required` — belong here, because they become Bean Validation annotations on the generated
DTO.

**Conventions**
- Kebab-case path segments; every list takes pagination → [spring patterns](../handbook/spring-patterns.md)
- A schema is defined **once** and `$ref`'d everywhere else → [openapi contract-first](../guides/openapi-contract-first.md)

**Avoid**
- Duplicating a schema shape in two operations — it produces two divergent generated classes and a type error that reads like a compiler bug

| Field | Value |
|---|---|
| **Produces** | The contract |
| **Verify** | `npx openapi-spec-validator src/main/resources/openapi/pokedex-api.yaml` |
| **Pass when** | Valid OpenAPI 3.1 |
| **On fail / Rollback** | Fix the YAML; the validator names the path |

### B2 — Define the ProblemDetail schema once

| Field | Value |
|---|---|
| **Type** | edit |
| **Target** | the same YAML |
| **Intent** | One error shape, `$ref`'d from every non-2xx response |
| **Depends on** | B1 |

**How**
RFC 9457 base members plus extensions `code`, `traceId`, `timestamp`, and `errors[]`
(`{field, message}`, present only on 400). `$ref` it from **every** error response on every
operation — including 502 and 504.

**Patterns**
- RFC 9457 as the envelope → [ADR-0003](../adr/0003-rfc9457-problemdetail.md)

| Field | Value |
|---|---|
| **Produces** | `ProblemDetailDTO` schema |
| **Verify** | `grep -c 'ProblemDetail' pokedex-api.yaml` |
| **Pass when** | Referenced by every documented error response; defined exactly once |
| **On fail / Rollback** | — |

### B3 — Wire the generator

| Field | Value |
|---|---|
| **Type** | config |
| **Target** | `pom.xml` |
| **Intent** | Interfaces and DTOs are generated, never hand-written |
| **Depends on** | B1 |

**How**
`openapi-generator-maven-plugin` bound to `generate-sources`. `generatorName=spring`,
`interfaceOnly=true`, **`useTags=true`**, `modelNameSuffix=DTO`,
`documentationProvider=springdoc`. One module, so it is configured and executed in the
single `pom.xml`.

> **`useTags=false` cannot satisfy this work unit's own exit criteria.** With it, the
> generator groups by the **first path segment**, and every path in this service begins
> `/v1` — the output is one `V1Api` carrying all 15 operations, not the `PokemonApi`,
> `LocalPokemonApi`, `SyncApi` and `SecurityApi` the Phase 1 gate names. The trap the
> original "Avoid" note described is real; the escape from it is `useTags=true` plus the
> four tags in [WF-000 §3.2](../workflows/WF-000-foundation.md), not a different set of tags
> under `useTags=false`.

**Avoid**
- Setting `useTags=false` and then re-tagging to fix the grouping — under that flag tags are ignored entirely and only the path shape matters → [openapi contract-first](../guides/openapi-contract-first.md)

| Field | Value |
|---|---|
| **Produces** | `target/generated-sources/openapi/**` |
| **Verify** | `mvn -B generate-sources && ls target/generated-sources/openapi/**/api/` |
| **Pass when** | `PokemonApi`, `LocalPokemonApi`, `SyncApi`, `SecurityApi` exist and compile |
| **On fail / Rollback** | Read the generator output — a YAML error usually surfaces here, not in B1 |

### B4 — Exclude generated sources from every gate

| Field | Value |
|---|---|
| **Type** | config |
| **Target** | `pom.xml` |
| **Intent** | Generated code is not our code; gating it produces noise, not signal |
| **Depends on** | B3 |

**How**
Exclude `**/generated-sources/**` from JaCoCo and the source-hygiene gate.

| Field | Value |
|---|---|
| **Produces** | Clean gate scope |
| **Verify** | `mvn -B verify` |
| **Pass when** | Coverage does not move when generated sources appear |
| **On fail / Rollback** | — |

### B5 — Serve the contract verbatim

| Field | Value |
|---|---|
| **Type** | config |
| **Target** | `web/config/OpenApiConfig.java`, `application.yaml` |
| **Intent** | A running instance must advertise exactly the contract we shipped |
| **Depends on** | B3 |

**How**
Point springdoc at the **static resource**, not at annotation scanning, so
`GET /api/v3/api-docs.yaml` returns the authored file byte-for-byte. Add a component test
asserting byte-identity (AC1c).

**Patterns**
- The served and authored contracts cannot diverge → [contract distribution](../diagrams/contract-distribution.md)

| Field | Value |
|---|---|
| **Produces** | `/v3/api-docs.yaml` |
| **Verify** | `diff <(curl -s localhost:8080/api/v3/api-docs.yaml) src/main/resources/openapi/pokedex-api.yaml` |
| **Pass when** | Empty diff |
| **On fail / Rollback** | Springdoc is generating from annotations — reconfigure it |

---

## Exit Criteria

- [x] Spec valid OpenAPI 3.1; generated interfaces compile
- [x] Every error response `$ref`s the one `ProblemDetail` schema
- [x] `size` declares default 10 and maximum 100
- [x] Served document byte-identical to the authored one — `OpenApiContractComponentTest`

```bash
mvn -B verify && make contract-check
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC1c, AC12, AC12b |
| Decision | [ADR-0002](../adr/0002-contract-first-openapi.md), [ADR-0008](../adr/0008-openapi-contract-distribution.md) |

## Blocks

Every story workflow · and every external consumer, which can start as soon as this exits

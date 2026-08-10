# WF-000 — Foundation

> **Scope**: The project setup, the contract, the domain core, and the governance every later workflow depends on. Not a user story — the substrate the stories are built on.
> **Project**: `pokedex-api`
> **Delivers**: Five-module build with every gate active · the OpenAPI contract · the domain model, state machine, and merge policy · the ArchUnit suite
> **Estimate**: L
> **Work units**: [WU-000-A](../work-units/WU-000-A-project-setup.md) · [WU-000-B](../work-units/WU-000-B-contract.md) · [WU-000-C](../work-units/WU-000-C-domain-core.md) · [WU-000-D](../work-units/WU-000-D-architecture-tests.md)
>
> **This workflow owns the shared specification.** The Information Asymmetry Register,
> the domain model, the formal constraints, the hard rules, and the ArchUnit rule table live
> here. Story workflows reference them; they do not restate them.

---

## 1. Summary

Produce a build in which the Clean Architecture dependency rule is a compile error, the API
contract exists before any controller, and the domain model — including the replication
state machine and the merge policy that protects curator data — is complete and
property-tested.

Nothing in this workflow is visible to a user. Everything in the story workflows depends on it.

---

## 2. Design Decisions

Recorded as ADRs rather than restated here:

| Decision | ADR |
|---|---|
| Clean Architecture as layered packages in one Maven module; boundaries enforced by ArchUnit | [ADR-0001](../adr/0001-clean-architecture-layered-packages.md) |
| Contract-first OpenAPI, DTOs generated | [ADR-0002](../adr/0002-contract-first-openapi.md) |
| RFC 9457 `ProblemDetail` as the error envelope | [ADR-0003](../adr/0003-rfc9457-problemdetail.md) |
| Java language level 24 on a Temurin 25 runtime | [ADR-0004](../adr/0004-java-24-language-level.md) |
| Re-sync merges; proprietary fields never overwritten | [ADR-0007](../adr/0007-proprietary-field-merge-policy.md) |
| The contract is a versioned, published artifact | [ADR-0008](../adr/0008-openapi-contract-distribution.md) |
| The service ships no client | [ADR-0009](../adr/0009-no-bundled-client.md) |
| Deletes are hard | [ADR-0010](../adr/0010-hard-deletes.md) |

---

## 3. Specification

### 3.0 Information Asymmetry Register

> Facts the principal knows that an executor would miss even after reading §3
> carefully. Each entry is a **hypothesis** until the `Verified by` action proves it.

| # | Tacit knowledge | Executor would assume | Reason it matters | Transferred to | Verified by |
|---|---|---|---|---|---|
| IA1 | `GET /api/v2/pokemon` returns **only** `{name, url}` — no sprite, weight, or abilities | The list endpoint carries the fields US01 needs | US01 silently under-delivers, or the fan-out is discovered mid-implementation and forces a re-plan | §1, §5.2, §7 DAG | `curl -s 'https://pokeapi.co/api/v2/pokemon?limit=1' \| jq '.results[0]\|keys'` → `["name","url"]` — **CONFIRMED 2026-08-09** |
| IA2 | Category is **not** on `/pokemon/{id}`. It is `genera[]` filtered to `language.name=="en"` → `.genus` on `/pokemon-species/{id}` | Category lives on the pokemon resource | A third upstream call per row; `1 + 2N` per page instead of `1 + N` | §3.2, §5.2 | `curl -s https://pokeapi.co/api/v2/pokemon-species/1 \| jq '.genera[]\|select(.language.name=="en").genus'` → `"Seed Pokémon"` — **CONFIRMED 2026-08-09** |
| IA3 | `weight` is in **hectograms** and `height` in **decimetres**, both integers | Kilograms and metres | Bulbasaur renders as "69 kg" instead of "6.9 kg" | §3.1 `Mass` VO, F8 | `curl -s https://pokeapi.co/api/v2/pokemon/1 \| jq '.weight,.height'` → `69`, `7` (= 6.9 kg, 0.7 m) — **CONFIRMED 2026-08-09** |
| IA4 | `flavor_text_entries[].flavor_text` contains literal `\n` and `\f` (form-feed) control characters | Clean prose | Description renders with visible artefacts or broken layout in the SPA | §3.1 `Description` VO, F9 | `curl -s https://pokeapi.co/api/v2/pokemon-species/1 \| jq -r '.flavor_text_entries[0].flavor_text' \| cat -A \| head -3` → shows `^L` — **CONFIRMED 2026-08-09** |
| IA5 | The evolution chain is a **recursive tree** (`chain.evolves_to[]` nested arbitrarily deep), not a list — Eevee branches to 8 children | A linear 3-stage array | A flat mapper truncates Eevee and every branching family | §3.1 `EvolutionLink`, §4.2 | `curl -s https://pokeapi.co/api/v2/evolution-chain/67 \| jq '.chain.evolves_to\|length'` → `8` — **CONFIRMED 2026-08-09** |
| IA6 | `species.names[]` already carries 12 localised names | Localised names must be authored by hand | US03's "localized nomenclature" can be **seeded** from upstream and then curator-overridden — a far better demo than empty fields | §3.1, §4.4 I5 | `curl -s https://pokeapi.co/api/v2/pokemon-species/1 \| jq '.names\|length'` → `12` — **CONFIRMED 2026-08-09** |
| IA7 | Spring Boot 4 / Framework 7 dropped the implicit primary-constructor heuristic. Any bean with ≥2 constructors **must** annotate the production one with `@Autowired` | Spring picks the longest-args constructor | `BeanInstantiationException` at boot or in MockMvc context — invisible at compile time | §9 Hard Rules | `grep -rn "public [A-Z][A-Za-z]*(" src/main/java/` — every class with ≥2 public ctors has exactly one `@Autowired`. **Verified by**: §0 pre-flight |
| IA8 | Spring Framework 7 renamed `HandlerMethodValidationException#getAllValidationResults()` → `getParameterValidationResults()` | The Framework 6 accessor exists | Compile failure in the exception handler, or a silently unmapped 500 | §9.5 | `javap -classpath <resolved spring-web jar> org.springframework.web.method.annotation.HandlerMethodValidationException`. **Verified by**: §0 runtime probe |
| IA9 | Bean validation on generated `*Api` interface params surfaces as **two** exception types: `ConstraintViolationException` (`@Validated` AOP proxy) and `HandlerMethodValidationException` (MVC method validation) | One exception type covers parameter validation | Half the 400s fall through to a generic 500 | §9.5 error matrix | `PokemonControllerValidationTest` asserts both produce an identical `ProblemDetail` shape |
| IA10 | PokeAPI is unauthenticated but **fair-use rate-limited**; a naive `from=1,to=1025` batch sync issues ~3,000 requests | Bulk sync can run unbounded | Upstream 429s, or a self-inflicted outage during the live demo | §5.5, R2 | Bounded `Semaphore(16)` + backoff; `PokeApiRateLimitComponentTest` |

### 3.1 Domain objects

| Object | Kind | Key fields | Notes |
|---|---|---|---|
| `Pokemon` | Aggregate root | `id`, `pokeApiId`, `name`, `category`, `mass`, `height`, `baseExperience`, `spriteUrl`, `description`, `replicationState`, `syncedAt`, `version` | `pokeApiId` nullable — a `DRAFT` record has none |
| `Pokemon.abilities` | Child entity | `id`, `name`, `slot`, `hidden` | The "skills" of US01 |
| `Pokemon.stats` | Child entity | `id`, `name`, `baseValue`, `effort` | US02 core statistics |
| `Pokemon.types` | Child entity | `id`, `name`, `slot` | |
| `Pokemon.tags` | Child entity, **proprietary** | `id`, `label` | US03 internal classification |
| `Pokemon.localizedNames` | Child entity, **proprietary** | `id`, `locale`, `value`, `source` | `source ∈ {UPSTREAM, CURATOR}` — seeded from `species.names[]`, curator-overridable |
| `Pokemon.region` | Value object, **proprietary** | `region` | US03 geographical metadata |
| `Pokemon.notes` | Value object, **proprietary** | `notes` | Free text, ≤ 2000 chars |
| `EvolutionLink` | Child entity | `id`, `fromPokeApiId`, `toPokeApiId`, `trigger`, `minLevel` | Flattened edge list of the upstream tree |
| `User` | Aggregate root | `id`, `username`, `email`, `passwordHash`, `roles`, `createdAt` | The spec's "secondary collection for user management" |
| `RefreshToken` | Child of `User` | `id`, `familyId`, `jti`, `expiresAt`, `revokedAt` | Rotation with family revocation. Built in [WU-AUTH-A](../work-units/WU-AUTH-A-user-domain.md), not WU-000-C |

> **The `id` on a child entity is a persistence concern, and the domain records do not carry
> one.** No invariant in §4.4 or §4.7 references a child's surrogate key — the containment
> rule `∀c ∈ Children: ∃!p ∈ Pokemon: c.pokemonId = p.id` is a foreign key with cascade,
> asserted by `PokemonCascadeDeleteComponentTest` (I9/F10), not by a field in `..domain..`.
> Re-sync replaces every replicated child wholesale, so a domain-side key would have to be
> either invented or null on each pass. The column is introduced with the JPA models in
> [WU-US03-A](../work-units/WU-US03-A-persistence.md).

### 3.3 Error envelope — RFC 9457

```json
{
  "type": "https://pokedex.elatus-dev.com/problems/pokemon-not-found",
  "title": "Pokemon not found",
  "status": 404,
  "detail": "No Pokemon with id 9999 exists",
  "instance": "/api/v1/pokedex/local/9999",
  "code": "POKEMON_NOT_FOUND",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "timestamp": "2026-08-09T14:22:31Z",
  "errors": [
    { "field": "region", "message": "must be one of KANTO, JOHTO, HOENN, …" }
  ]
}
```

`code`, `traceId`, `timestamp`, and `errors[]` are RFC 9457 extension members. `errors[]` appears only on 400.

---

## 4. Domain Model

> **Industry reference**: Domain-Driven Design (Eric Evans), C4 Model (Simon Brown)

### 4.1 Aggregates

> Each aggregate has exactly one root — all external references go through the root.
> Aggregate boundaries = transaction boundaries = consistency boundaries.

> **Diagram**: [Domain aggregates](../diagrams/domain-aggregates.md) — rendered there with the rules it encodes.

Cross-aggregate references are **id-only**, never object references — `Pokemon.curatedBy` holds a `UserId`, not a `User`. This keeps each aggregate independently loadable and honours `B-31` (never modify two aggregates in one transaction).

### 4.2 Entity Relationships

> **Industry reference**: Entity-Relationship Diagram (Chen notation / Mermaid erDiagram)

> **Diagram**: [Entity relationships](../diagrams/entity-relationship.md) — rendered there with the rules it encodes.

Both top-level tables satisfy the exercise's data requirement — a unique primary key plus well beyond the minimum of two descriptive attributes. **Deletes are hard** ([ADR-0010](../adr/0010-hard-deletes.md)). There is no `deletedAt` column, no `@SQLRestriction` on every query, and no recoverable state: `DELETE` removes the row, and getting a Pokémon back means re-syncing it, which produces a new record.

### 4.3 State Machine

> Valid transitions with guards. Every transition must follow this diagram — no skipping states.
> Terminal states marked `[*]`; invalid transitions documented as comments.

> **Diagram**: [Replication state machine](../diagrams/replication-state-machine.md) — rendered there with the rules it encodes.

The state machine exists to make one rule unavoidable: the only edge that writes replicated fields from upstream is `STALE → {SYNCED, CUSTOMIZED}`, and the `CUSTOMIZED` variant is guarded by the merge policy (I5/F7). There is no path on which upstream data overwrites curator data.

### 4.4 Domain Invariants

> Rules that must always hold. If you can't write a test for it, it's not an invariant.

| # | Invariant | Enforced By | When | Test |
|---|---|---|---|---|
| I1 | `pokeApiId` is unique across the catalogue when non-null | DB partial unique index + `DuplicatePokemonException` | On create and on link | `PokemonUniquenessComponentTest` |
| I2 | `name` is non-blank and ≤ 60 characters | `PokemonName` VO constructor | On construction | `PokemonNameTest` |
| I3 | `mass` and `height` are strictly positive | `Mass` / `Height` VO constructors | On construction | `MassTest`, `HeightTest` |
| I4 | A `Pokemon` carries at most 10 tags, each ≤ 30 chars, case-insensitively distinct | `Pokemon.addTag` | On mutation | `PokemonTagLimitTest` |
| I5 | Re-synchronisation never modifies a proprietary field | `PokemonMergePolicy` | On `STALE → *` transition | `PokemonMergePolicyTest` |
| I6 | `replicationState` transitions follow §4.3 exactly | `ReplicationState.transitionTo` | On every state change | `ReplicationStateTransitionTest` |
| I7 | `region` is a member of the closed `Region` enum | `Region` VO | On construction | `RegionTest` |
| I8 | A refresh-token family has at most one live token | `RefreshTokenRotationUseCase` | On refresh | `RefreshTokenReuseComponentTest` |
| I9 | Deleting a `Pokemon` removes its children — abilities, stats, types, tags, localized names, evolution links | Cascade + orphan removal on the aggregate | On delete | `PokemonCascadeDeleteComponentTest` |
| I10 | A `User` password is never stored in clear, logged, or returned | `PasswordHash` VO + `@JsonIgnore` + log audit | Always | `UserSerializationTest`, `/audit-security` |
| I11 | Every mutating endpoint requires an authenticated principal | `SecurityFilterChain` terminating in `anyRequest().authenticated()` | Every request | `AuthEnforcementComponentTest` |

### 4.5 Value Objects

> Immutable domain concepts — no identity. All are Java `record`s (`java:S6206`).

| Value Object | Fields | Immutability | Equality |
|---|---|:---:|---|
| `PokemonId` | `value: Long` | Immutable | By value |
| `PokeApiId` | `value: Integer` | Immutable | By value; `> 0` enforced |
| `PokemonName` | `value: String` | Immutable | Case-insensitive |
| `Mass` | `hectograms: Integer` | Immutable | By value; exposes `toKilograms(): BigDecimal` |
| `Height` | `decimetres: Integer` | Immutable | By value; exposes `toMetres(): BigDecimal` |
| `Category` | `value: String` | Immutable | By value |
| `Description` | `value: String` | Immutable | By value; normalises `\n` and `\f` on construction |
| `Sprite` | `frontDefault: URI, officialArtwork: URI` | Immutable | By both components |
| `Region` | `value: RegionCode` (enum) | Immutable | By enum identity |
| `Tag` | `label: String` | Immutable | Case-insensitive |
| `Notes` | `value: String` | Immutable | By value; ≤ 2000 chars — the proprietary free-text field of §3.1 |
| `Email` | `value: String` | Immutable | Case-insensitive, normalised |
| `Username` | `value: String` | Immutable | Case-insensitive, normalised; 3..30 chars |
| `UserId` | `value: Long` | Immutable | By value |
| `PasswordHash` | `value: String` | Immutable | By value; `toString()` returns `"***"` |
| `ReplicationState` | enum | Immutable | By enum identity — lives in `..domain.model..`, not `..domain.vo..`, because it carries the transition guard |

### 4.6 Domain Events

| Event | Trigger | Consumers | Async? |
|---|---|---|:---:|
| `PokemonReplicated` | `PENDING → SYNCED` | Cache eviction for `pokemon:{id}`; audit log | Yes |
| `PokemonCustomized` | `SYNCED → CUSTOMIZED` | Audit log | Yes |
| `PokemonDeleted` | `DELETE` on any state | Cache eviction; audit log | Yes |
| `RefreshTokenReuseDetected` | Reuse of a rotated token | Family revocation; security audit log | No — must be synchronous |

### 4.7 Formal Domain Model

> **Industry reference**: Z Notation (Spivey), Alloy (Jackson), Design by Contract (Meyer).
> §4.2 and §4.3 are for orientation; this section is for unambiguous constraint
> specification. Every formula below appears in the `Test / AC` column — formulas
> without test mappings are cheap talk.

#### Notation Legend

| Symbol | Meaning | Example |
|---|---|---|
| `∈` | Element of | `p ∈ Pokemon` |
| `∅` | Empty set | `tags = ∅` |
| `⊥` | Undefined / absent | `pokeApiId = ⊥` |
| `∀` | For all | `∀p ∈ Pokemon: P(p)` |
| `∃!` | There exists exactly one | `∃!u ∈ User: …` |
| `⟹` | Implies | `P ⟹ Q` |
| `∧` `∨` `¬` | And · Or · Not | `P ∧ ¬Q` |
| `≡` | Defined as | `total ≡ Σ(…)` |
| `x'` | Post-state of `x` | `state' = SYNCED` |
| `\|x\|` | Cardinality | `\|tags\| ≤ 10` |
| `⇀` | Partial function | `curated_by: Pokemon ⇀ User` |
| `R⁺` | Transitive closure of relation `R` | `evolves_to⁺` |
| `ℤ₊` | Positive integers | `mass ∈ ℤ₊` |
| `PRE` / `POST` / `INV` | Pre-, post-condition, invariant | — |

#### Entity Sets

| Set | Description | Attributes | Cardinality |
|---|---|---|---|
| `User` | Registered curators | `{id: ℤ, username: String, email: String, roles: ℙ(Role)}` | `\|User\| ≥ 0` |
| `Pokemon` | Locally replicated Pokémon | `{id: ℤ, pokeApiId: ℤ ∪ {⊥}, name: String, mass: ℤ₊, state: State, syncedAt: Instant ∪ {⊥}}` | `\|Pokemon\| ≥ 0` |
| `Tag` | Proprietary classification labels | `{id: ℤ, pokemonId: ℤ, label: String}` | `\|Tag\| ≥ 0` |
| `LocalizedName` | Proprietary nomenclature | `{id: ℤ, pokemonId: ℤ, locale: String, value: String, source: {UPSTREAM, CURATOR}}` | `\|LocalizedName\| ≥ 0` |
| `State` | `{DRAFT, PENDING, SYNCED, CUSTOMIZED, STALE, FAILED}` | enum | `\|State\| = 6` |

Define the two field projections of a `Pokemon`:

- `Proprietary ≡ {region, notes, tags, localizedNames[source = CURATOR], curatedBy}`
- `Replicated ≡ {name, category, mass, height, baseExperience, spriteUrl, description, abilities, stats, types, evolutionLinks, localizedNames[source = UPSTREAM]}`

By construction `Proprietary ∩ Replicated = ∅`. That disjointness is exactly what makes the merge in **F7** total and unambiguous — every field belongs to precisely one side, so re-sync needs no conflict-resolution policy.

#### Relations

| Relation | Definition | Cardinality Constraint |
|---|---|---|
| `curated_by` | `Pokemon ⇀ User` (partial) | `∀p ∈ Pokemon: p.curatedBy ≠ ⊥ ⟹ ∃!u ∈ User: u.id = p.curatedBy` |
| `annotated_with` | `Pokemon × Tag` | `∀g ∈ Tag: ∃!p ∈ Pokemon: g.pokemonId = p.id` |
| `named_in` | `Pokemon × LocalizedName` | `∀n ∈ LocalizedName: ∃!p ∈ Pokemon: n.pokemonId = p.id` |
| `holds` | `User × RefreshToken` | `∀t ∈ RefreshToken: ∃!u ∈ User: t.userId = u.id` |
| `evolves_to` | `PokeApiId × PokeApiId` | Acyclic: `∀(a,b) ∈ evolves_to⁺: a ≠ b` |

#### Domain Constraints (Invariants)

| # | Formal Constraint | Natural Language | §4.4 Ref | Test / AC |
|---|---|---|---|---|
| F1 | `∀p₁, p₂ ∈ Pokemon: (p₁.pokeApiId = p₂.pokeApiId ∧ p₁.pokeApiId ≠ ⊥) ⟹ p₁ = p₂` | `pokeApiId` is unique when present | I1 | `PokemonUniquenessComponentTest` / AC3 |
| F2 | `∀p ∈ Pokemon: 1 ≤ \|p.name\| ≤ 60 ∧ p.name ≠ blank` | Name is non-blank, ≤ 60 chars | I2 | `PokemonNameTest` |
| F3 | `∀p ∈ Pokemon: p.mass ∈ ℤ₊ ∧ p.height ∈ ℤ₊` | Mass and height are strictly positive | I3 | `MassTest`, `HeightTest` |
| F4 | `∀p ∈ Pokemon: \|p.tags\| ≤ 10 ∧ ∀g₁,g₂ ∈ p.tags: lower(g₁.label) = lower(g₂.label) ⟹ g₁ = g₂` | ≤ 10 tags, case-insensitively distinct | I4 | `PokemonTagLimitTest` |
| F5 | `∀p ∈ Pokemon: p.state ∈ {SYNCED, CUSTOMIZED, STALE} ⟹ p.syncedAt ≠ ⊥` | Any replicated state implies a sync timestamp | I6 | `ReplicationStateTransitionTest` |
| F6 | `∀p ∈ Pokemon: p.state = DRAFT ⟺ p.pokeApiId = ⊥` | `DRAFT` is exactly the set of unlinked records | I6 | `ReplicationStateTransitionTest` |
| F7 | `∀p ∈ Pokemon, ∀u ∈ Upstream: reSync(p, u) ⟹ p'.Proprietary = p.Proprietary ∧ p'.Replicated = u.Replicated` | Re-sync replaces replicated fields and preserves proprietary fields exactly | I5 | `PokemonMergePolicyTest` / AC5 |
| F8 | `massKg ≡ p.massHectograms / 10 ∧ heightM ≡ p.heightDecimetres / 10` | Upstream units are hectograms and decimetres | IA3 | `MassConversionTest` |
| F9 | `∀d ∈ Description: ¬contains(d.value, '\n') ∧ ¬contains(d.value, '\f')` | Descriptions are normalised free of control characters | IA4 | `DescriptionNormalizationTest` |
| F10 | `delete(p) ⟹ p ∉ Pokemon ∧ ∀c ∈ children(p): c ∉ Child` | Deleting a root removes it and every child; no orphans remain | I9 | `PokemonCascadeDeleteComponentTest` |
| F11 | `∀f ∈ TokenFamily: \|{t ∈ f : t.revokedAt = ⊥ ∧ t.expiresAt > now}\| ≤ 1` | At most one live token per refresh family | I8 | `RefreshTokenReuseComponentTest` / AC4 |
| F12 | `∀(a,b) ∈ evolves_to⁺: a ≠ b` | The evolution graph is acyclic | — | `EvolutionChainMapperTest` |

#### Operations (Pre/Post Conditions)

| Operation | PRE | POST | Error on PRE violation | Test / AC |
|---|---|---|---|---|
| `syncPokemon(pokeApiId)` | `authenticated ∧ pokeApiId ∈ ℤ₊ ∧ upstream(pokeApiId) ≠ ⊥` | `∃!p ∈ Pokemon: p.pokeApiId = pokeApiId ∧ p.state' = SYNCED ∧ p.syncedAt' = now` | `PokemonNotFoundUpstreamException` / 404; `UpstreamUnavailableException` / 502 | `SyncPokemonUseCaseTest` / AC2 |
| `reSync(id)` | `p.state ∈ {STALE, FAILED}` | `p.Proprietary' = p.Proprietary ∧ p.Replicated' = upstream.Replicated ∧ p.state' ∈ {SYNCED, CUSTOMIZED}` | `IllegalStateTransitionException` / 409 | `PokemonMergePolicyTest` / AC5 |
| `createLocal(cmd)` | `authenticated ∧ cmd.name ≠ blank ∧ (cmd.pokeApiId = ⊥ ∨ ∄p: p.pokeApiId = cmd.pokeApiId)` | `∃!p: p.id = newId ∧ p.state' = DRAFT` | `DuplicatePokemonException` / 409; `ValidationException` / 400 | `CreateLocalPokemonUseCaseTest` / AC2, AC3 |
| `updateLocal(id, cmd)` | `∃p: p.id = id ∧ cmd.version = p.version` | `p.Proprietary' = cmd.Proprietary ∧ p.version' = p.version + 1 ∧ p.state' = CUSTOMIZED` | `PokemonNotFoundException` / 404; `OptimisticLockException` / 412; `ValidationException` / 400 | `UpdateLocalPokemonUseCaseTest` / AC2, AC3 |
| `deleteLocal(id)` | `∃p ∈ Pokemon: p.id = id` | `p ∉ Pokemon' ∧ ∀c ∈ children(p): c ∉ Child'` | `PokemonNotFoundException` / 404 | `DeleteLocalPokemonUseCaseTest` / AC2 |
| `listPokemon(page, size)` | `page ≥ 0 ∧ 1 ≤ size ≤ 100`, defaults `page = 0`, `size = 10` | `\|result\| ≤ size ∧ ∀r ∈ result: r.sprite ≠ ⊥ ∧ r.category ≠ ⊥ ∧ r.mass ≠ ⊥ ∧ r.abilities ≠ ∅` | `ValidationException` / 400 | `ListPokemonUseCaseTest` / AC2 |
| `refreshToken(t)` | `t.revokedAt = ⊥ ∧ t.expiresAt > now` | `t.revokedAt' = now ∧ ∃!t' ∈ family(t): t'.revokedAt = ⊥` | Reuse ⟹ revoke entire family / 401 | `RefreshTokenReuseComponentTest` / AC4 |

#### Derived Values

| Derived Value | Definition | Consumers | Test / AC |
|---|---|---|---|
| `pokemon.massKilograms` | `≡ massHectograms / 10` | List row, detail view | `MassConversionTest` |
| `pokemon.heightMetres` | `≡ heightDecimetres / 10` | Detail view | `HeightConversionTest` |
| `pokemon.isStale` | `≡ syncedAt + TTL < now` | Re-sync scheduler, `stale` response flag | `StalenessPredicateTest` |
| `pokemon.displayName` | `≡ localizedNames[locale = req.locale, source = CURATOR] ?? localizedNames[locale = req.locale, source = UPSTREAM] ?? name` | SPA header, list row | `DisplayNameResolutionTest` |
| `page.totalPages` | `≡ ⌈totalElements / size⌉`, where `size ∈ [1, 100]`, default 10 | Pagination control | `PaginationMetadataTest` |
| `page.upstreamCallCount` | `≡ 1 + 2·size` on a cold cache — 21 at default, 201 at maximum | Capacity reasoning, not a response field | `FanOutCallCountTest` |
| `syncBatch.summary` | `≡ (\|succeeded\|, \|failed\|, \|skipped\|)`, where the three sets partition the requested range | Batch 202 response | `BatchSyncSummaryTest` |

#### Composition Rules

| Aggregate Root | Contains | Containment Rule | Lifecycle |
|---|---|---|---|
| `Pokemon` | `PokemonAbility`, `PokemonStat`, `PokemonType`, `PokemonTag`, `LocalizedName`, `EvolutionLink` | `∀c ∈ Children: ∃!p ∈ Pokemon: c.pokemonId = p.id` | Cascade all, orphan removal |
| `User` | `RefreshToken` | `∀t ∈ RefreshToken: ∃!u ∈ User: t.userId = u.id` | Cascade all, orphan removal |

---

## 5. Architecture

Diagrams are canonical in [`../diagrams/`](../diagrams) and are not duplicated here.

| Concern | Diagram |
|---|---|
| Context and containers | [c4-context-container.md](../diagrams/c4-context-container.md) |
| Package dependencies | [package-dependencies.md](../diagrams/package-dependencies.md) |
| Element relationships | [element-relationships.md](../diagrams/element-relationships.md) |
| Deployment | [deployment.md](../diagrams/deployment.md) |
| Contract distribution | [contract-distribution.md](../diagrams/contract-distribution.md) |
| ArchUnit enforcement | [archunit-enforcement.md](../diagrams/archunit-enforcement.md) |
| Work unit order | [work-unit-dag.md](../diagrams/work-unit-dag.md) |

### Module / Folder Structure

```
pokedex-api/
├── pom.xml                                   ← one module, no parent
└── src/
    ├── main/java/com/elatusdev/pokedex/
    │   ├── domain/          model · vo · policy · exception · port   ← depends on nothing
    │   ├── application/     usecase · command · result
    │   ├── infrastructure/  persistence · pokeapi · cache · security
    │   ├── web/             controller · error · config
    │   └── PokedexApplication.java
    ├── main/resources/
    │   ├── openapi/pokedex-api.yaml          ← the contract, authored by hand
    │   └── db/migration/                     ← Flyway
    └── test/java/com/elatusdev/pokedex/
        └── architecture/                     ← ArchUnit, imports target/classes
```

There is no `frontend/` directory and no Node in the toolchain. This is a service.

---

## 8. Infrastructure Changes

### Docker Compose services

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment: [POSTGRES_DB=pokedex, POSTGRES_USER=pokedex]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U pokedex"], interval: 5s, retries: 10 }
    volumes: [pgdata:/var/lib/postgresql/data]
  redis:
    image: redis:7-alpine
    command: ["redis-server", "--appendonly", "yes"]
    healthcheck: { test: ["CMD", "redis-cli", "ping"], interval: 5s, retries: 10 }
  api:
    build: { context: ., dockerfile: Dockerfile }
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
    ports: ["8080:8080"]
volumes: { pgdata: }
```

No `web` service. This service ships no client — see [ADR-0009](../adr/0009-no-bundled-client.md).

### New dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-web` | 4.1.0 | MVC servlet stack (**not** WebFlux) |
| `spring-boot-starter-data-jpa` | 4.1.0 | Hibernate 7 |
| `spring-boot-starter-data-redis` | 4.1.0 | Cache + session store |
| `spring-boot-starter-security` | 4.1.0 | Filter chain, `@PreAuthorize` |
| `spring-boot-starter-validation` | 4.1.0 | Bean Validation |
| `flyway-core`, `flyway-database-postgresql` | 13.2.0 | Versioned schema + seed |
| `io.jsonwebtoken:jjwt-*` | 0.13.0 | ES256 sign/verify |
| `openapi-generator-maven-plugin` | 7.24.0 | `generatorName=spring`, `modelNameSuffix=DTO` |
| `springdoc-openapi-starter-webmvc-ui` | 3.1.0 | Swagger UI serving the hand-written spec |
| `org.wiremock:wiremock-standalone` | 3.13.1 | PokeAPI contract tests |
| `openapi-spec-validator`, `oasdiff` | — | `make contract-check`: spec validity and breaking-change detection |
| `org.pitest:pitest-maven` + `pitest-junit5-plugin` | 1.25.9 / 1.2.3 | Mutation testing on `domain` and `application` |
| `org.testcontainers:{postgresql,junit-jupiter}` | 1.21.4 | Component tests |
| `com.tngtech.archunit:archunit-junit5` | 1.5.0 | Architecture suite |
| `org.jacoco:jacoco-maven-plugin` | 0.8.15 | Coverage, merged across tiers |

### Environment variables

| Variable | Default (dev) | Notes |
|---|---|---|
| `POKEDEX_DB_URL` | `jdbc:postgresql://postgres:5432/pokedex` | |
| `POKEDEX_REDIS_HOST` | `redis` | |
| `POKEAPI_BASE_URL` | `https://pokeapi.co/api/v2` | Overridden to WireMock in tests |
| `POKEAPI_CACHE_TTL` | `PT24H` | ISO-8601 duration |
| `POKEAPI_MAX_CONCURRENCY` | `16` | Semaphore permits |
| `JWT_KEYSTORE_PATH` / `JWT_KEYSTORE_PASSWORD` | dev keystore | **Never** committed with a real password |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `PT15M` / `P7D` | |

Secrets never live in `*.properties` (`java:S6437`, `docker:S6472`). The dev keystore is a throwaway generated by `make keys`.

---

## 9. Constraints & Prerequisites

### Prerequisites

- JDK 24-compatible toolchain (Temurin 25 installed, `maven.compiler.release=24`)
- Docker with a running daemon — required for Testcontainers component tests
- Outbound HTTPS to `pokeapi.co` for the contract-refresh task (tests themselves use WireMock and run offline)
- Node is **not** required. This repository contains no browser code — [ADR-0009](../adr/0009-no-bundled-client.md)

### Hard Rules

> All code produced must comply with the ElatusDev platform standards:
> `CODE-QUALITY-STANDARD` · `ANTI-PATTERNS-BACKEND` · `TESTING-STANDARD-BACKEND` ·
> `SECURITY-QUALITY-STANDARD` · `SONARCLOUD-QUALITY-GATE-STANDARD`.

| # | Rule | Source |
|---|---|---|
| H1 | `..domain..` references **no** framework type — no Spring, JPA, Jakarta, or Jackson. One module means the compiler will not stop you; **ArchUnit `L2` will** | `B-35`, [ADR-0001](../adr/0001-clean-architecture-layered-packages.md) |
| H2 | API DTOs are **generated from OpenAPI**, never hand-written. Consumers generate their own types from the published contract | `B-20` (CRITICAL) |
| H3 | Constructor injection only. Any bean with ≥2 constructors annotates the production one `@Autowired` | `java:S6813`, IA7 |
| H4 | `RestClient` for HTTP. `RestTemplate`, `WebClient`, and WebFlux are forbidden | `spring-boot.md` |
| H5 | One `throw` per method, a **specific domain exception**, translated at the `@RestControllerAdvice` | `java.md` |
| H6 | Never catch `Exception` or `Throwable`; no empty catch blocks | `B-05`, `B-06` |
| H7 | `Optional` at return boundaries; never `null` for an absent value or an empty collection | `java:S2789`, `java:S1168` |
| H8 | Records for immutable data; `Stream.toList()`; text blocks for multi-line SQL/JSON | `java:S6206`, `java:S6204`, `java:S6126` |
| H9 | Method body ≤ 20 lines; class ≤ 300 lines (500 for entities/config/controllers); cyclomatic ≤ 10; nesting ≤ 3 | `B-01`–`B-04` |
| H10 | IDs are always `Long`, never `Integer` (`pokeApiId` is the documented exception — it is an upstream identifier, not ours) | `B-14` |
| H11 | Copyright header `Copyright (c) 2026 ElatusDev` in the first 10 lines of every `.java` and `.ts`/`.tsx` | `U-01` |
| H12 | Date literals use `java.time.Month`; `.now()` always takes a `Clock` or `ZoneId` | `java:S8694`, `java:S8688` |
| H13 | JWT is **ES256**; reject `alg:none`; validate `iss`/`aud`/`exp`; key by `kid` | `jwt-jose.md`, `java:S5659` |
| H14 | Passwords hashed with BCrypt; `HashingService`-style SHA-256 is **not** for passwords | `java:S5344` |
| H15 | Deny by default — every `SecurityFilterChain` terminates in `.anyRequest()` | `SB-PA4`, OWASP A01 |
| H16 | Bind to request DTOs, never to entities (mass assignment) | OWASP A08, `java:S4684` |
| H17 | No PII or secrets in logs; no `System.out` | OWASP A09, `java:S106` |
| H18 | Suppression ladder: `sonar.exclusions` and `// NOSONAR` are **forbidden**. Only `@SuppressWarnings("java:SNNNN")` with a one-line WHY comment, narrowest scope | `SONARCLOUD-QUALITY-GATE-STANDARD` |
| H19 | `mvn -B verify` is the build command. `-DskipITs` / `-Dmaven.failsafe.skip=true` are BLOCKER violations | `TBX-001..003` |
| H20 | Integration tests are named `*ComponentTest.java` (Failsafe), unit tests `*Test.java` (Surefire) | `TESTING-STANDARD-BACKEND` |
| H25 | **No `any()` matchers.** Stubs and verifications use exact values, or `argThat` with a real predicate | AC10 |
| H26 | **No Javadoc.** Names carry *what*, tests carry *how*, ADRs carry *why*. A `//` comment explaining a non-obvious *why* is permitted. Generated sources excluded | [java patterns](../handbook/java-patterns.md) |
| H27 | `domain` has no logger and does not log. Never log a token, password hash, PII, or full body. Fan-out is logged as a **summary** | [logging](../handbook/logging.md) |
| H28 | **TDD.** Every class in `..domain..` and `..application..` is written test-first: red, green, refactor. Adapters are driven by their contract tests. Test and implementation commit together, test first in the diff | [`CLAUDE.md`](../../CLAUDE.md#tdd-is-not-optional) |
| H21 | Structured concurrency is **banned** (preview at language level 24). Virtual threads + `CompletableFuture` instead | ADR-0004 |
| H22 | The published OpenAPI document is **append-or-version**: a breaking change to an existing operation requires a new path version, never an in-place edit | [ADR-0008](../adr/0008-openapi-contract-distribution.md) |
| H23 | Every release publishes `pokedex-api.yaml` as an asset tagged with the release version. A release without a published spec is a failed release | [ADR-0008](../adr/0008-openapi-contract-distribution.md) |
| H24 | CORS is an explicit origin allow-list per environment. Never `*` | OWASP A05, `java:S5122` |

### Architecture Rules (ArchUnit)

> Every rule below is asserted by a test in `src/test/java/.../architecture/`, which imports
> `target/classes` across the whole project. Listing a rule here without a test
> is cheap talk — the audit flags it.

| Rule | Description | Applies? | Verification |
|---|---|:---:|---|
| **L1** — Layer direction | `application` must not depend on `web` or `infrastructure` | Yes | `LayerArchitectureTest` |
| **L2** — Domain purity | `domain` must not depend on Spring, JPA, Jakarta, or any other module | Yes | `DomainPurityArchitectureTest` |
| **L3** — Controller isolation | `*Controller` must not depend on `*Repository` or `*DataModel` | Yes | `LayerArchitectureTest` |
| **L4** — Use-case isolation | `*UseCase` must not import `org.springframework.web..` nor expose `*DataModel` | Yes | `LayerArchitectureTest` |
| **N1** — UseCase naming | `*UseCase` resides in `..application.usecase..` | Yes | `NamingConventionArchitectureTest` |
| **N2** — Controller naming | `*Controller` resides in `..web.controller..` | Yes | `NamingConventionArchitectureTest` |
| **N3** — Repository naming | `*Repository` port in `..domain.port..`; adapter in `..infrastructure.persistence..` | Yes | `NamingConventionArchitectureTest` |
| **N4** — DataModel naming | `*DataModel` resides in `..infrastructure.persistence.model..` | Yes | `NamingConventionArchitectureTest` |
| **N5** — Exception placement | Domain exceptions are `RuntimeException` subclasses in `..domain.exception..`; no `*Service` inside `usecase` | Yes | `NamingConventionArchitectureTest` |
| **IO1** — DB containment | `EntityManager`, `JdbcTemplate`, and `JpaRepository` appear only under `..infrastructure..` | Yes | `IoConfinementArchitectureTest` |
| **IO2** — HTTP containment | `RestClient` appears only under `..infrastructure.pokeapi..` | Yes | `IoConfinementArchitectureTest` |
| **IMF1** — Immutability | Every class in `..domain.vo..` is a `record` or has only final fields | Yes | `ImmutabilityArchitectureTest` |
| **CI1** — Constructor injection | No field `@Autowired`, no field `@Value`, no setter injection | Yes | `ConstructionArchitectureTest` |
| **SB-PA4** — Filter-chain fallthrough | Every `@Bean SecurityFilterChain` terminates with `.anyRequest()` | Yes | `SecurityConfigArchitectureTest` |
| **OA1** — Contract confinement | Every `@RestController` implements a generated `*Api` interface | Yes | `OpenApiContractConfinementArchitectureTest` |
| **CY1** — No cycles | No package cycles anywhere in `com.elatusdev.pokedex` | Yes | `CycleArchitectureTest` |

`FreezingArchRule` is forbidden — rules ship enforced or not at all.

### Out of Scope

- HashiCorp Vault; secrets come from environment variables
- Certificate authority / trust broker; the ES256 keypair is a local keystore
- HMAC request signing, device/token binding, IP allowlisting
- i18n message bundles; `ProblemDetail` carries English text plus a stable machine-readable `code`
- Mobile client, GraphQL, event sourcing, service mesh
- **Any client** — this service ships none. It owns the published contract and nothing about who calls it ([ADR-0009](../adr/0009-no-bundled-client.md))

---

## 9.5 Error & Edge Case Paths — shared

The cross-cutting rows. Story-specific error paths live in their own workflow.

| Step | Error condition | System response | Recovery |
|---|---|---|---|
| Request binding | Malformed JSON | 400 `MALFORMED_REQUEST` | Fix the payload |
| Request binding | Bean Validation failure | 400 `VALIDATION_ERROR` + `errors[]` | Correct the field |
| Request binding | Parameter validation — **two** exception types (IA9) | 400 `VALIDATION_ERROR`, identical shape from both | — |
| Request binding | `size > 100`, `size < 1`, or `page < 0` | 400 `INVALID_PAGINATION` naming the cap | Resend within `1..100` |
| Persistence | Connection pool exhausted | 503 + `Retry-After` | Auto-heals |
| Persistence | Flyway checksum mismatch | Boot **fails fast** | Fix the migration; never edit an applied one |
| Any | Unmapped exception | 500 `INTERNAL_ERROR`, logged with the trace id, **no stack trace in the body** | — |

Full matrix: [error handling](../handbook/error-handling.md).

---

## 10. Acceptance Criteria

**AC1**: Given a clean checkout, when `make verify` runs, then all four layer packages compile at
language level 24, every test tier passes, and JaCoCo reports ≥ 90% line and ≥ 90% branch.

**AC1c**: Given a running API, when `GET /api/v3/api-docs.yaml` is called, then the document
is **byte-identical** to `src/main/resources/openapi/pokedex-api.yaml`.

**AC5 — Merge policy**: Given a `CUSTOMIZED` record with `region`, `notes`, and 3 tags, when
re-sync runs against changed upstream data, then every replicated field updates and **every
proprietary field is byte-identical to its prior value** (F7).

**AC6 — Coverage**: JaCoCo on merged Surefire and Failsafe data reports ≥ 90% line and
≥ 90% branch; the build **fails** below either.

**AC9 — Architecture**: All 16 ArchUnit rules pass, none frozen, none allowlisted.

**AC9b — Copyright**: Every `.java` outside generated sources carries the header.

**AC9c — No Javadoc**: `grep -rn --include=*.java '/\*\*' src/main/java` returns nothing outside generated sources.

**AC9d — Log hygiene**: No log statement emits a token, password hash, email, or full request body; `domain` contains no logger. A cold page emits **one** summary line, not 2N.

**AC10 — Unit tests**: Every test asserts state **and** interactions with exact args,
explicit `times(1)`, and `verifyNoMoreInteractions`. **No test uses `any()`.**

**AC10b — Mutation score**: ≥ 85% on `domain`, ≥ 75% on `application`. Every survivor fixed
or excluded with a written justification. This doubles as the TDD signal — retrofitted tests
score measurably worse.

**AC12 — API contract**: Every response matches the declared schema, status code, and
content type; error responses validate against the `ProblemDetail` schema.

**AC12b — Contract validity and stability**: `make contract-check` reports the spec valid
OpenAPI 3.1, and `oasdiff` reports no breaking change to an existing operation against the
last tag.

### Cheap Talk Inventory

| Section | Claims found | Action taken |
|---|:---:|---|
| §4.4 | 0 | Every invariant carries a named test |
| §4.7 | 0 | Every formula maps to a test or AC; every `PRE` violation has an error row |
| §5 | 3 | Layer, naming, and contract rules promoted to ArchUnit L1–L4, N1–N5, OA1 |
| §9 | 2 | Copyright promoted to a `validate`-phase check; suppression ladder to a grep target in `make verify` |
| §10 | 4 | Hosted-analyser gates (duplication, smells, hotspots) **deleted rather than restated** — nothing local enforces them |
| §10 AC10 | 1 | "Verifies interactions" was unfalsifiable while `any()` was permitted — promoted to H25 and AC10b |
| §9 H26 | 1 | "No Javadoc" was a preference until it had a grep gate — promoted to AC9c |

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R3 | Generator misconfigured, discovered after controllers are written | Med | High | R | WU-000-B precedes all controller work; the DAG forbids the reverse |
| R4 | Coverage gate fails late and blocks the build | Med | High | R | JaCoCo enforced from WU-000-A so it never becomes a cliff |
| R5 | Spring Boot 4 multi-constructor regression breaks boot (IA7) | Med | High | R | §0 pre-flight grep; `ApplicationContextLoadsComponentTest` is the first component test written |
| R6 | The merge policy has a hole and re-sync destroys curator data | Low | High | Y | F7 is a **property** test over generated field combinations, plus a PIT threshold |
| R8 | Component tests skipped because Docker is absent, and nobody notices | Med | High | R | `make verify` **fails** rather than skips |
| R9 | A breaking contract change ships silently | Med | High | R | `make contract-check` runs `oasdiff` against the last tag |

---

## Where the real detail lives

| Need | Read |
|---|---|
| Why a decision was made | [The ADR index](../../README.md) |
| The rules the executor must not break | [`../../CLAUDE.md`](../../CLAUDE.md) |
| Build order across all workflows | [`../prompts/pokedex-api-prompt.md`](../prompts/pokedex-api-prompt.md) |
| Activity-level implementation | [`../work-units/`](../work-units) |

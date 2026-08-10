# WU-000-D — Architecture Tests

| Field | Value |
|---|---|
| **Work Unit** | WU-000-D |
| **Parent** | [WF-000 Foundation](../workflows/WF-000-foundation.md) |
| **Objective contribution** | The 16 structural rules the POM cannot express |
| **Estimate** | S |
| **Status** | not started |

## Objective

Assert on compiled bytecode across the whole project, so a layering violation is a build
failure rather than a review comment.

## Entry Criteria

- WU-000-A green. Build it **early**, in parallel with everything else — rules that exist
  before the code they govern fail on the first violation rather than during a cleanup pass.

## Inputs

| Input | Source | Used by |
|---|---|---|
| Rule table | [WF-000 §9](../workflows/WF-000-foundation.md) | I1–I5 |
| Rule rationale | [archunit-enforcement.md](../diagrams/archunit-enforcement.md) | I1–I5 |

## Outputs

- `architecture` test package with 16 passing rules, none frozen

---

## ▶ Activity Sequence

### I1 — Reactor-wide importer

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `src/test/java/.../architecture/ProjectClasses.java` |
| **Intent** | One suite, one package tree |
| **Depends on** | — (entry) |

**How**
`ClassFileImporter` walking `target/classes`, excluding generated sources and tests.
Cache the imported classes in a static so 16 rules do not re-import 16 times.

| Field | Value |
|---|---|
| **Produces** | Shared importer |
| **Verify** | `mvn -B test -Dtest='*ArchitectureTest'` |
| **Pass when** | Imports classes from all four modules |
| **On fail / Rollback** | Ensure the importer points at `target/classes`, not `src` |

### I2 — Layer and purity rules (L1–L4)

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `LayerArchitectureTest.java`, `DomainPurityArchitectureTest.java` |
| **Intent** | Catch what the module graph cannot |
| **Depends on** | I1 |

**How**
`L1` application must not depend on web or infrastructure · `L2` domain must not depend on
Spring, JPA, or Jakarta · `L3` controllers must not touch `*Repository`/`*DataModel` ·
`L4` use cases must not import `org.springframework.web..` nor return `*DataModel`.

| Field | Value |
|---|---|
| **Produces** | Four rules |
| **Verify** | Add a deliberate violation, run, confirm red, revert |
| **Pass when** | The violation fails the build naming the class |
| **On fail / Rollback** | Move the dependency — **never** suppress the rule |

### I3 — Naming and containment (N1–N5, IO1–IO2)

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `NamingConventionArchitectureTest.java`, `IoConfinementArchitectureTest.java` |
| **Intent** | A class's package tells you what it may touch |
| **Depends on** | I1 |

**How**
`*UseCase` → `..application.usecase..` · `*Controller` → `..web.controller..` ·
`*Repository` port → `..domain.port..`, adapter → `..infrastructure.persistence..` ·
`*DataModel` → `..infrastructure.persistence.model..` · exceptions in `..domain.exception..`,
no `*Service` inside `usecase` · `EntityManager`/`JdbcTemplate`/`JpaRepository` only under
`..infrastructure..` · `RestClient` only under `..infrastructure.pokeapi..`.

| Field | Value |
|---|---|
| **Produces** | Seven rules |
| **Verify** | `mvn -B test -Dtest='*ArchitectureTest'` |
| **Pass when** | All green against the real codebase |
| **On fail / Rollback** | Move the class |

### I4 — Immutability, construction, security, contract (IMF1, CI1, SB-PA4, OA1)

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `ImmutabilityArchitectureTest.java`, `ConstructionArchitectureTest.java`, `SecurityConfigArchitectureTest.java`, `OpenApiContractConfinementTest.java` |
| **Intent** | The four rules most likely to be violated under deadline |
| **Depends on** | I1 |

**How**
`IMF1` every class in `..domain.vo..` is a record or has only final fields · `CI1` no field
`@Autowired`, no field `@Value`, no setter injection · `SB-PA4` every `@Bean
SecurityFilterChain` terminates with `.anyRequest()` · `OA1` every `@RestController`
implements a generated `*Api`.

| Field | Value |
|---|---|
| **Produces** | Four rules |
| **Verify** | `mvn -B test -Dtest='*ArchitectureTest'` |
| **Pass when** | Green |
| **On fail / Rollback** | — |

### I5 — Cycle freedom (CY1)

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `CycleArchitectureTest.java` |
| **Intent** | A package cycle is a design error that compiles fine |
| **Depends on** | I1 |

**How**
`slices().matching("com.elatusdev.pokedex.(*)..").should().beFreeOfCycles()`.

**Avoid**
- `FreezingArchRule`. A frozen baseline turns a rule into advice, and advice does not survive a deadline → [archunit governance](../guides/archunit-governance.md)

| Field | Value |
|---|---|
| **Produces** | Cycle rule |
| **Verify** | `mvn -B test -Dtest='*ArchitectureTest'` |
| **Pass when** | No cycles |
| **On fail / Rollback** | Extract the shared type, usually into `domain` |

---

## Exit Criteria

- [ ] All 16 rules pass
- [ ] **No `FreezingArchRule`, no allowlists, no `@ArchIgnore`**
- [ ] Each rule demonstrated to fail on a deliberate violation, then reverted

```bash
mvn -B test -Dtest='*ArchitectureTest'
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC9 |
| Decision | [ADR-0001](../adr/0001-clean-architecture-layered-packages.md) |

## Blocks

WU-999-B

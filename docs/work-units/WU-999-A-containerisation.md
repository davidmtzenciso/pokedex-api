# WU-999-A — Containerisation and Seed

| Field | Value |
|---|---|
| **Work Unit** | WU-999-A |
| **Parent** | [WF-999 Delivery](../workflows/WF-999-delivery.md) |
| **Objective contribution** | One command brings the stack up with real data |
| **Estimate** | M |
| **Status** | done |

## Objective

Container image, compose stack, seed data, the contract check, and the E2E collection —
everything needed for a cold `docker compose up --build` to reach a working API.

## Entry Criteria

- Every story workflow green

## Inputs

| Input | Source | Used by |
|---|---|---|
| Deployment topology | [deployment.md](../diagrams/deployment.md) | J2 |
| Gate chain | [verification-gates.md](../diagrams/verification-gates.md) | J5 |

## Outputs

- `Dockerfile`, `docker-compose.yml`, `V2__seed.sql`, `make contract-check`, Newman collection

---

## ▶ Activity Sequence

### J1 — Multi-stage `Dockerfile`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `Dockerfile`, `.dockerignore` |
| **Intent** | A small, non-root, reproducible image |
| **Depends on** | — (entry) |

**How**
**Three stages**, because the build cache and the image-layer cache are different problems:

1. **build** — `eclipse-temurin:25-jdk`, digest-pinned. `mvn dependency:go-offline` in its own layer *before* `COPY src`, then `mvn package -DskipTests`.
2. **extract** — `java -Djarmode=tools -jar …-jar extract --layers`, splitting the fat jar into `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`.
3. **runtime** — `eclipse-temurin:25-jre`, digest-pinned, non-root `USER`, layers copied **least-changing first**, exec-form `ENTRYPOINT` with `-XX:MaxRAMPercentage=75`, `HEALTHCHECK` against **`/actuator/health/readiness`** with `start-period=40s`.

`.dockerignore` must exclude `target/`, `.git/`, and **`keys/`** — a build context containing
the dev keystore puts it in the image.

Full rationale, with the Dockerfile: [containerization](../handbook/containerization.md).

**Conventions**
- Digest-pinned base (`docker:S6596`, `S8431`); non-root `USER` (`docker:S6471`); no secret in `ARG`/`ENV` (`docker:S6472`); exec-form `ENTRYPOINT` (`docker:S7019`)
- Hand-written Dockerfile rather than buildpacks → [ADR-0011](../adr/0011-container-image-strategy.md)

**Avoid**
- `COPY . .` before dependency resolution — invalidates the build cache on every source change
- **Skipping the layer extraction.** A multi-stage build without it looks correct and quietly reships ~60 MB of unchanged dependencies on every code change
- `-Xmx` in the Dockerfile — it hard-codes a number that is wrong the moment the container limit changes. Use `MaxRAMPercentage`
- Pointing the healthcheck at `/actuator/health` — a Redis outage would then restart-loop the API, which fixes nothing and wipes the cache each time. Use **readiness**
- Shell-form `ENTRYPOINT` — `/bin/sh` does not forward `SIGTERM`, so graceful shutdown never runs

| Field | Value |
|---|---|
| **Produces** | The image |
| **Verify** | `docker build -t pokedex-api . && hadolint Dockerfile` |
| **Pass when** | Builds; `hadolint` clean; `docker run --rm pokedex-api id -u` is not 0; a one-line source change rebuilds **only** the application layer |
| **On fail / Rollback** | — |

### J2 — `docker-compose.yml`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `docker-compose.yml` |
| **Intent** | Postgres, Redis, API — and nothing else |
| **Depends on** | J1 |

**How**
`postgres:17-alpine` with a `pg_isready` healthcheck and a named volume; `redis:7-alpine`
with `--appendonly yes` and a `redis-cli ping` healthcheck; the API with
`depends_on: condition: service_healthy` on both, plus an explicit
`deploy.resources.limits.memory: 1g` — without it `MaxRAMPercentage` is a percentage of
the whole host.

Also set `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 20s`
so `SIGTERM` drains in-flight requests rather than dropping them.

**Avoid**
- A `web` service. This repository ships no client → [ADR-0009](../adr/0009-no-bundled-client.md)
- `depends_on` without `condition` — bare `depends_on` waits for the container to *start*, not to be *usable*, so Flyway races an initialising Postgres
- Omitting the memory limit — the JVM then sizes its heap against the host, not the container

| Field | Value |
|---|---|
| **Produces** | The stack |
| **Verify** | `docker compose down -v && docker compose up --build` |
| **Pass when** | Health `UP` with **zero manual steps** |
| **On fail / Rollback** | `docker compose down -v` |

### J3 — `V2__seed.sql`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `src/main/resources/db/migration/V2__seed.sql` |
| **Intent** | A demo that is fast and shows proprietary fields populated |
| **Depends on** | — (entry) |

**How**
The original 151 with children, plus **populated** `region`, `notes`, and `tags` on a
representative subset — US03 is unconvincing against empty fields. Users `demo` and `admin`
with BCrypt hashes. Seeding also removes the cold-path latency that is risk R1.

**Avoid**
- Seeding a plaintext password. Hash it in the migration

| Field | Value |
|---|---|
| **Produces** | Seed data |
| **Verify** | `curl -s localhost:8080/api/v1/pokedex/local \| jq '.totalElements'` |
| **Pass when** | 151; demo credentials authenticate |
| **On fail / Rollback** | `docker compose down -v` |

### J4 — `make contract-check`

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `Makefile` |
| **Intent** | A breaking contract change fails before it reaches a consumer |
| **Depends on** | — (entry) |

**How**
`openapi-spec-validator` on the committed spec, then `oasdiff breaking` against the spec at
the last git tag. Wire into `make verify`.

**Patterns**
- Contract as a versioned artifact → [ADR-0008](../adr/0008-openapi-contract-distribution.md)

| Field | Value |
|---|---|
| **Produces** | The contract gate |
| **Verify** | Introduce a breaking rename, run, confirm red, revert |
| **Pass when** | `oasdiff` names the breaking operation |
| **On fail / Rollback** | Add a versioned path instead of editing in place |

### J5 — Newman E2E collection

| Field | Value |
|---|---|
| **Type** | create |
| **Target** | `e2e/pokedex-api.postman_collection.json`, `make e2e` |
| **Intent** | Prove the wiring against a running stack |
| **Depends on** | J2, J3 |

**How**
The nine mandatory scenarios per entity — create 201, create duplicate 409, get 200, get
404, list 200, update 200, update 404, delete 204, get-after-delete 404 — plus auth
enforcement (401) and the AC5 merge assertion. Assert exact status, `Content-Type`, and body
fields.

**Avoid**
- Asserting only status codes. A 200 with the wrong body passes

| Field | Value |
|---|---|
| **Produces** | E2E collection |
| **Verify** | `make e2e` |
| **Pass when** | All requests green against the seeded stack |
| **On fail / Rollback** | — |

### J6 — README and Makefile completion

| Field | Value |
|---|---|
| **Type** | edit |
| **Target** | `README.md`, `Makefile` |
| **Intent** | A reviewer cloning cold gets running in under five minutes |
| **Depends on** | J2, J5 |

**How**
Confirm every command in the README actually works from a clean clone. Complete `make
verify` as the full chain.

| Field | Value |
|---|---|
| **Produces** | Working docs |
| **Verify** | Clone to a temp dir, follow the README verbatim |
| **Pass when** | Every command succeeds as written |
| **On fail / Rollback** | Fix the README, not your memory of it |

---

## Exit Criteria

- [ ] Cold `docker compose up --build` reaches a healthy API, zero manual steps
- [ ] Served contract byte-identical to the authored one (AC1c)
- [ ] Seeded data present; demo credentials work
- [ ] `hadolint` clean; `gitleaks detect` finds nothing
- [ ] `make e2e` green

```bash
docker compose down -v && docker compose up --build && make e2e && make contract-check
```

## Traceability

| Satisfies | Reference |
|---|---|
| Workflow AC | AC-999-1 … AC-999-4 |
| Decision | [ADR-0008](../adr/0008-openapi-contract-distribution.md), [ADR-0009](../adr/0009-no-bundled-client.md) |

## Blocks

WU-999-B

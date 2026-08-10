# WF-999 — Delivery and Verification

> **Scope**: Containerisation, seed data, the E2E collection, and the final audit. Not a user story — the release-readiness workflow.
> **Depends on**: every other workflow
> **Delivers**: `docker compose up --build` reaching a working API, and an honest execution report
> **Estimate**: M
> **Work units**: [WU-999-A](../work-units/WU-999-A-containerisation.md) · [WU-999-B](../work-units/WU-999-B-verification.md)

---

## 1. Summary

Everything that is true of the whole system rather than of any one story: the image, the
compose stack, the seeded demo data, the end-to-end collection, and the audit that says
plainly what works and what does not.

---

## 2. Design decisions specific to delivery

| # | Decision | Alternatives | Rationale | Consequence |
|---|---|---|---|---|
| 1 | Compose runs postgres + redis + api only | A full-stack compose pulling a client image | A colocated client makes this repo own the client's release cycle | A full demo is two terminals — [ADR-0009](../adr/0009-no-bundled-client.md) |
| 2 | Seed the original 151 **with proprietary fields populated** | Empty catalogue; empty custom fields | US03 is unconvincing against empty fields, and seeding removes the cold-path latency that is R1 | A larger `V2` migration |
| 3 | Local gates only, `make verify` as the gate of record | A hosted pipeline | The project builds and runs locally; a gate nobody can run is cheap talk | Nothing makes it unskippable — stated, not hidden |
| 4 | Hand-written layered Dockerfile | `spring-boot:build-image` (buildpacks); single-stage | A reviewer can interrogate every property of the image; a buildpack image is a black box whose good defaults I did not choose | We own the base-image lifecycle — [ADR-0011](../adr/0011-container-image-strategy.md) |

---

## 3. Specification

| Artifact | Contents |
|---|---|
| `Dockerfile` | **Three-stage** — build, layer-extract, runtime. Digest-pinned `eclipse-temurin:25-jre`, non-root `USER`, exec-form entrypoint, `MaxRAMPercentage=75`, healthcheck on **readiness**. See [containerization](../handbook/containerization.md) |
| `.dockerignore` | Excludes `target/`, `.git/`, and **`keys/`** |
| `docker-compose.yml` | postgres + redis + api, healthchecks, `condition: service_healthy`, explicit memory limit |
| `V2__seed.sql` | The original 151 with children, populated `region`/`notes`/`tags` on a subset, users `demo` and `admin` |
| `Makefile` | `verify`, `keys`, `up`, `down`, `e2e`, `contract-check`, `mutation` |
| `e2e/` | Newman collection — nine mandatory scenarios per entity plus auth and the merge assertion |

---

## 5. Flow

[deployment.md](../diagrams/deployment.md) ·
[verification-gates.md](../diagrams/verification-gates.md)

---

## 10. Acceptance criteria

**AC-999-1**: Given a clean checkout, when `docker compose up --build` runs, then Postgres
and Redis report healthy, Flyway applies `V1` and `V2`, and the API answers
`/api/actuator/health` with `UP` — **zero manual steps**.

**AC-999-2**: Given a running API, then `GET /api/v3/api-docs.yaml` is byte-identical to the
authored spec (AC1c).

**AC-999-3**: Given the seeded stack, when `make e2e` runs, then every request asserts an
exact status, `Content-Type`, and body — including the nine mandatory scenarios per entity
and the AC5 merge assertion.

**AC-999-4**: Given `gitleaks detect` and `hadolint`, then both are clean, the dev keystore
is gitignored, and `docker history` shows no secret in any `ARG` or `ENV`.

**AC-999-4b**: Given a one-line change to a domain class, when the image is rebuilt, then
**only the application layer** is rebuilt — the dependency layers are cache hits.

**AC-999-4c**: Given a running container, then `id -u` is not 0, `SIGTERM` drains in-flight
requests, and the healthcheck targets **readiness**, not `/health`.

**AC-999-5**: Given a fresh clone in a temp directory, when `make verify` runs, then it is
green with no manual intervention.

**AC-999-6**: Given `make mutation`, then ≥ 85% on `domain` and ≥ 75% on `application`, with
every survivor fixed or excluded with a written justification.

**AC-999-7**: Given the execution report, then every AC across every workflow has a verdict
and evidence, and "What's Still Missing" is filled in truthfully.

---

## 11. Execution report

Two parts. Narrative first — what was done, before/after metrics, a feature map, what this
enables, and **what is still missing**. Then technical detail — result, metrics, files,
dependencies, deviations, verification output, known issues, and the AC status table.

A report that claims completeness it does not have destroys the credibility of every other
claim in it.

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R1 | Cold fan-out makes the demo slow | High | High | R | Seed the 151; warm the cache on startup; demo from the seeded state |
| R8 | Component tests skipped because Docker is absent | Med | High | R | `make verify` fails rather than skips |
| R10 | Scope creep from "any additional functionality is welcome" | High | Low | Y | Every AC across every workflow green first; extras only after |
| R10b | A local-only gate is skipped under deadline pressure | Med | Med | Y | One command, plus a pre-push hook. Honestly: nothing makes it unskippable |

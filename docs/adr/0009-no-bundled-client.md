# ADR-0009: The Service Ships No Client

**Status**: Accepted
**Date**: 2026-08-09
**Deciders**: David Martinez

## Context

The exercise requires a browser client. The tempting shape is to bundle it here — a `frontend/` directory in this repository, a `web` service in this compose file, one command that demos everything.

It is convenient, and it quietly makes this repository responsible for something it should not own. A backend that builds, versions, tests, and deploys a browser client is no longer a service with an API; it is an application with an internal HTTP boundary. The API contract stops being a contract, because both sides move together and nothing forces either to respect it.

The deeper problem is that it produces a false signal. An endpoint change that a colocated client absorbs silently would break any real consumer, and nothing in the build would say so.

## Decision

This repository is a service. It ships an API and a published contract, and nothing else.

- `docker compose up` starts **postgres + redis + api**. There is no `web` service, no `frontend/` directory, and no Node in the toolchain.
- Clients are separate, independently released, and unknown to this repository. They integrate through the artifact published by [ADR-0008](0008-openapi-contract-distribution.md) and through nothing else.
- CORS is an explicit allow-list of permitted origins, configured per environment. Never `*`.
- The build here never compiles, tests, or packages a client, and needs no toolchain for one — there is no Node in this repository.

The obligation runs one way: we publish a stable, versioned contract. What a consumer does with it — which language, which generator, which release cadence — is entirely its own concern.

## Alternatives Considered

1. **Colocate the client in this repository** — One clone, one command, simplest demo. Rejected because it couples release cycles, leaves the contract untested *as a contract*, and means a breaking change stays invisible until an external consumer appears. It optimises the demo at the expense of the architecture the demo exists to show.
2. **Separate client repository, but a full-stack compose here that pulls its image** — Preserves the one-command demo while keeping the code apart. Rejected because it makes this repository's compose file own the client's image tag: every client release then needs a bump here. That is the same coupling with extra indirection.
3. **A third "deployment" repository owning the umbrella compose** — Correct for a real multi-service estate. Rejected as disproportionate for one service, and it would need its own release process to solve a problem that two `docker compose up` commands already solve.
4. **Git submodule of a client** — Rejected. Submodules restore the coupling of a monorepo with none of its ergonomics, and reliably confuse anyone cloning cold.

## Consequences

### Positive
- The API is genuinely consumable by anything, because it has never been tuned to one caller.
- This repository can be cloned, built, tested, and run with no knowledge of any client.
- The build is self-contained: `make verify` needs this repository and Docker, nothing else.
- The contract is exercised as a contract, which is the only way to find out whether it is a good one.

### Negative
- **A full-stack demo needs two terminals** — this service first, wait for healthy, then a client. For a live presentation that is a small but real cost. Mitigate by rehearsing the order and stating it in the README.
- Local development of any client requires this service running, or a mock derived from the contract.
- CORS must be configured and kept correct, where a same-origin bundle would have avoided the question entirely.

### Neutral
- In a real deployment a static client could sit behind the same ingress, making the two same-origin; CORS is a development-time concern.
- If the demo friction proves genuinely costly, alternative 2 is the successor — not a reversal of the separation.

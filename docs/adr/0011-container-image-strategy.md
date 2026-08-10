# ADR-0011: A Hand-Written Layered Dockerfile, Not Buildpacks

**Status**: Accepted
**Date**: 2026-08-10
**Deciders**: David Martinez

## Context

Spring Boot ships `spring-boot:build-image`, which produces an OCI image with Cloud Native Buildpacks and no Dockerfile at all. It is one Maven goal, it layers correctly by default, it runs as non-root by default, and it applies sensible JVM container ergonomics without being asked. On the merits of "produce a good image with the least effort", it wins.

The exercise requires "a Dockerfile for containerized execution" — but that phrasing is about the deliverable, not the mechanism, and a buildpack image would arguably satisfy the intent.

So the question is not "which produces a better image by default", because buildpacks do. It is which one a reviewer can read and interrogate.

## Decision

Hand-write a multi-stage Dockerfile.

- **Build stage** — `eclipse-temurin:25-jdk`, digest-pinned. `mvn dependency:go-offline` in its own layer before `COPY src`, so a source change does not re-resolve dependencies.
- **Extract stage** — `java -Djarmode=tools -jar … extract --layers`, splitting the fat jar into `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`.
- **Runtime stage** — `eclipse-temurin:25-jre`, digest-pinned, non-root `USER`, layers copied **least-changing first**, exec-form `ENTRYPOINT`, `HEALTHCHECK` against **readiness**.
- `-XX:MaxRAMPercentage=75` paired with an explicit memory limit in compose.

Full rationale for each: [containerization](../handbook/containerization.md).

The image is **not** where tests run. `make verify` owns that, and `-DskipTests` in the Dockerfile is the single place that flag is acceptable.

## Alternatives Considered

1. **`spring-boot:build-image` (buildpacks)** — Better defaults, less code, no base-image maintenance. Rejected for one reason: in a code review the panel can read a Dockerfile and ask "why JRE not JDK", "why is this layer first", "why readiness not health" — and each has an answer that demonstrates understanding. A buildpack image is a black box whose good properties I did not choose and cannot explain. For a technical exercise where the *reasoning* is the deliverable, that is the wrong trade. In a production estate with many services, the trade reverses and buildpacks win.
2. **Single-stage Dockerfile with a fat jar** — Simplest possible. Rejected: a JDK in the runtime image adds ~70 MB and a compiler to the attack surface, and one fat layer means every code change re-ships all dependencies.
3. **Multi-stage but no layered jar** — What most examples do. Rejected because it looks correct while quietly wasting the image cache: a one-line domain change reships ~60 MB. The layer extraction is three extra lines.
4. **Distroless runtime** (`gcr.io/distroless/java25`) — Smallest surface, no shell, no package manager. Genuinely tempting. Rejected because no shell means no `docker exec` to look around when something is wrong during a live demo, and the diagnostic cost outweighs the marginal security gain for a local-only exercise. Worth revisiting for a real deployment.
5. **Alpine base** — ~40 MB smaller. Rejected: musl libc has produced enough JVM edge cases that the saving is not worth the class of bug it invites.

## Consequences

### Positive
- Every property of the image is a decision someone made and can defend — which is the point in a code review.
- Layered caching means a code-only rebuild ships kilobytes, not tens of megabytes.
- Digest-pinned bases make the build genuinely reproducible; a tag is mutable and quietly is not.
- Non-root, no secrets in `ARG`/`ENV`, exec-form entrypoint, readiness-based healthcheck — each satisfying a specific Sonar Docker rule rather than being cargo-culted.

### Negative
- **We now own the base image lifecycle.** Digest pinning means CVE patches require a deliberate bump; buildpacks would have handled it. Nothing here automates that, and pretending otherwise would be dishonest — it is a manual step that will be forgotten if this ran for a year.
- More surface to get wrong: layer order, signal handling, user creation, and healthcheck target are all things buildpacks would have got right unasked.
- The Dockerfile must be kept in step with the Java version in `pom.xml`. Two places, one fact.

### Neutral
- `hadolint` in `make verify` catches the common Dockerfile mistakes, which narrows the gap with buildpacks' defaults.
- If this service joined an estate with several others, alternative 1 becomes the right answer and this ADR should be superseded rather than edited.

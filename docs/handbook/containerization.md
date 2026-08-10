# Containerization

The image is a build artifact with the same standards as the code. Everything below exists
because of a specific failure it prevents.

---

## Two caches, and they are not the same

This is the thing most Spring Boot Dockerfiles get wrong.

| Cache | What it saves | How you get it |
|---|---|---|
| **Build cache** | Re-downloading dependencies at build time | `mvn dependency:go-offline` in its own layer, before `COPY src` |
| **Image layer cache** | Re-*shipping* ~60 MB of unchanged dependencies on every code change | **Layered jars** — `layertools` extracting the jar into ordered layers |

A Dockerfile with only the first still rebuilds one fat 70 MB layer every time a domain
class changes. With layered jars, a code-only change re-ships a few hundred kilobytes.

```dockerfile
# --- build ---
FROM eclipse-temurin:25-jdk@sha256:<digest> AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline           # build cache: deps in their own layer
COPY src src
COPY scripts scripts
RUN mvn -B clean package -DskipTests       # tests already ran in `make verify`

# --- extract layers ---
FROM build AS extract
WORKDIR /build/target
RUN java -Djarmode=tools -jar pokedex-api-*.jar extract --layers --destination extracted

# --- runtime ---
FROM eclipse-temurin:25-jre@sha256:<digest>
WORKDIR /app
RUN useradd --system --uid 1001 --no-create-home pokedex
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/dependencies/ ./
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/application/ ./
USER pokedex
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
```

The `COPY` order is deliberate: **least-changing first**. Dependencies change on a version
bump, application classes change on every commit. Reversing them defeats the whole exercise.

> `-Djarmode=tools … extract` is the Spring Boot 3.3+ form. The older
> `-Djarmode=layertools … extract` still works but is deprecated.

---

## Base image

**`eclipse-temurin:25-jre`, digest-pinned.** Three parts, each load-bearing:

| Choice | Why |
|---|---|
| **JRE, not JDK** | The runtime stage needs no compiler. Roughly 70 MB saved and a smaller attack surface |
| **Temurin 25** | Matches [ADR-0004](../adr/0004-java-24-language-level.md) — language level 24, runtime on current LTS |
| **Digest-pinned** | `docker:S6596`, `docker:S8431`. A tag is mutable; `:25-jre` is a different image next month, so a "reproducible" build is not |

Alpine is not used: its musl libc has caused enough JVM surprises that the ~40 MB is not
worth the class of bug. Distroless was considered and rejected in
[ADR-0011](../adr/0011-container-image-strategy.md) — no shell means no `docker exec` when
something is wrong at 9 a.m. on demo day.

---

## JVM ergonomics in a container

The JVM is container-aware since 10, but its **default max heap is 25% of the container
limit**. On a 1 GB limit that is a 256 MB heap while 750 MB sits unused.

```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
```

Paired with an explicit limit in compose, so the percentage is a percentage of something known:

```yaml
api:
  deploy:
    resources:
      limits: { memory: 1g }
```

**Never set `-Xmx` in the Dockerfile.** It hard-codes a number that is wrong the moment the
limit changes. The percentage adapts; the absolute does not.

Virtual threads need no tuning — that is the point of them ([concurrency](concurrency.md)).

---

## Graceful shutdown

A container gets `SIGTERM` and then, some seconds later, `SIGKILL`. Without configuration
the JVM drops in-flight requests at the first signal.

```yaml
server.shutdown: graceful
spring.lifecycle.timeout-per-shutdown-phase: 20s
```

The `ENTRYPOINT` is **exec form** (`["java", …]`, not `java …`), so the JVM is PID 1 and
receives the signal directly. Shell form wraps it in `/bin/sh`, which does not forward
signals — the JVM never learns it is being shut down (`docker:S7019`).

---

## Health: the container check and the endpoints are different questions

```dockerfile
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/actuator/health/readiness || exit 1
```

| Probe | Question | Fails when |
|---|---|---|
| **Liveness** | Is the JVM wedged? | Only on unrecoverable state. Restarting fixes it |
| **Readiness** | Can it serve traffic? | Postgres or Redis is unreachable. Restarting does **not** fix it |

Pointing the container healthcheck at plain `/health` conflates them: a Redis outage would
restart the API in a loop, which fixes nothing and destroys the Redis cache each time.
Point it at **readiness**.

`start-period=40s` covers Flyway migrations on a cold database. Too short and the container
is killed mid-migration.

---

## Compose

```yaml
services:
  postgres:
    image: postgres:17-alpine@sha256:<digest>
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U pokedex"], interval: 5s, retries: 10 }
    volumes: [pgdata:/var/lib/postgresql/data]
  redis:
    image: redis:7-alpine@sha256:<digest>
    command: ["redis-server", "--appendonly", "yes"]
    healthcheck: { test: ["CMD", "redis-cli", "ping"], interval: 5s, retries: 10 }
  api:
    build: { context: ., dockerfile: Dockerfile }
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
    deploy: { resources: { limits: { memory: 1g } } }
    ports: ["8080:8080"]
volumes: { pgdata: }
```

- **`condition: service_healthy`, never bare `depends_on`.** Bare `depends_on` waits for the container to *start*, not to be *usable* — so Flyway races an initialising Postgres and the API dies on a cold boot.
- **`appendonly yes`** so the cache and `jti` sessions survive a restart. Losing sessions on restart logs everyone out mid-demo.
- **No `web` service.** This repository ships no client — [ADR-0009](../adr/0009-no-bundled-client.md).

---

## `.dockerignore`

Without it, `COPY` ships `target/`, `.git/`, and — worst — `keys/`.

```
target/
keys/
.git/
.idea/
docs/
*.log
```

A build context containing the dev keystore puts it in the image. `gitleaks` catches the
commit; nothing catches the `COPY`.

---

## What the build does *not* do

**Tests do not run in the Dockerfile.** `make verify` already ran them, and running them
again in the image build doubles the loop, needs a Docker daemon inside the build, and
produces a confusing second source of truth about whether the code is green.

`-DskipTests` in the Dockerfile is therefore correct — and it is the **only** place that
flag is acceptable. In `mvn verify` it is a BLOCKER violation.

---

## Verification

```bash
docker build -t pokedex-api .
hadolint Dockerfile
docker run --rm pokedex-api id -u          # must not be 0
docker image inspect pokedex-api --format '{{.Size}}' | numfmt --to=iec
docker compose down -v && docker compose up --build
```

| Check | Pass when |
|---|---|
| `hadolint` | Clean |
| Runs as non-root | `id -u` is not 0 (`docker:S6471`) |
| Layer caching works | A one-line source change rebuilds only the application layer |
| Cold start | `docker compose up --build` reaches health `UP` with **zero manual steps** |
| No secrets | `docker history` shows no `ARG`/`ENV` secret (`docker:S6472`); `keys/` absent from the image |

---

## Related

[ADR-0011](../adr/0011-container-image-strategy.md) (why a Dockerfile rather than buildpacks) ·
[deployment diagram](../diagrams/deployment.md) ·
[WU-999-A](../work-units/WU-999-A-containerisation.md) (the activities) ·
[logging](logging.md) (structured JSON in the container profile)

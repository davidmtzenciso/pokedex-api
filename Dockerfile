# Three stages, because the build cache and the image-layer cache are different problems:
# the first stops Maven re-resolving dependencies on a code change, the second stops Docker
# re-shipping them. A multi-stage build without the layer extraction looks correct and
# quietly reships ~60 MB of unchanged dependencies on every commit.

# --- build ---
# Maven rather than a bare JDK: the temurin images carry no mvn, so `mvn` in a JDK stage
# fails at build time rather than at review time.
FROM maven:3.9-eclipse-temurin-25@sha256:1b1fc6d0168ea616afd1c861d6f32ec37c9ec2ffe88a0351b3771dd4ad86b0d8 AS build
WORKDIR /build
COPY pom.xml .
# its own layer, before COPY src — otherwise every source edit re-resolves every dependency
RUN mvn -B dependency:go-offline
COPY src src
COPY scripts scripts
# the tests already ran in `make verify`; running them again here would make the image build
# the slowest gate in the loop and would need a Docker daemon inside the build
RUN mvn -B clean package -DskipTests

# --- extract layers ---
FROM build AS extract
WORKDIR /build/target
# renamed here, not in the runtime stage: the extracted jar carries the project version in
# its name, and hard-coding that into COPY and ENTRYPOINT would break the image on every
# version bump
RUN java -Djarmode=tools -jar pokedex-api-*.jar extract --layers --destination extracted \
    && mv extracted/application/*.jar extracted/application/application.jar

# --- runtime ---
FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0
WORKDIR /app

# Alpine, for two reasons found by checking the image rather than trusting it: the Ubuntu
# JRE base carries no curl, wget, nc or busybox, so the HEALTHCHECK could never succeed and
# `depends_on: service_healthy` would never be satisfied — and it is 540 MB against 304 MB.
# busybox wget covers the healthcheck; keytool is in the JRE for the entrypoint's fallback.
# busybox adduser creates no matching group, so the group comes first — otherwise the
# chown fails with 'invalid group' and the build stops here
RUN addgroup -S -g 1001 pokedex \
    && adduser -S -u 1001 -G pokedex -H pokedex \
    && mkdir -p /app/keys \
    && chown -R pokedex:pokedex /app

# least-changing first: dependencies move on a version bump, application classes on every
# commit. Reversing these four lines defeats the extraction entirely.
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/dependencies/ ./
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=pokedex:pokedex /build/target/extracted/application/ ./
COPY --chown=pokedex:pokedex scripts/docker-entrypoint.sh /app/docker-entrypoint.sh

USER pokedex
EXPOSE 8080

# readiness, not health. /actuator/health goes DOWN when Redis does, and an unhealthy
# container is restarted — which fixes nothing, wipes the cache, and turns a degraded cache
# into a restart loop.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
  CMD ["/bin/sh", "-c", "wget -qO- http://localhost:8080/api/actuator/health/readiness || exit 1"]

# exec form: the JVM is PID 1 and receives SIGTERM, so graceful shutdown actually runs.
# The entrypoint script ends in `exec java`, which preserves that.
ENTRYPOINT ["/app/docker-entrypoint.sh"]

# Troubleshooting — symptom, cause, fix

So you spend two minutes on a known failure mode instead of forty.

**Read it when…** something is broken. Scan the symptom column first.

---

## Startup

| Symptom | Likely cause | Fix |
|---|---|---|
| `BeanInstantiationException` at boot, but everything compiles | A Spring bean has two constructors and neither carries `@Autowired`. Spring Boot 4 dropped the implicit primary-constructor heuristic | Annotate the production constructor `@Autowired` |
| Flyway refuses to start: checksum mismatch | An already-applied migration was edited | Never edit an applied migration. Add a new one, or wipe the dev volume: `docker compose down -v` |
| App starts, every request 401 | Redis is unreachable, so session checks fail closed | Start Redis. The behaviour is deliberate — see [security-auth.md](../guides/security-auth.md) |
| `Unable to load keystore` | `make keys` was never run, or `JWT_KEYSTORE_PATH` is wrong | `make keys` |
| Port 8080 already in use | A previous `spring-boot:run` is still alive | `lsof -ti:8080 \| xargs kill` |

## Build

| Symptom | Likely cause | Fix |
|---|---|---|
| Cannot find symbol: `PokemonApi` / `PokemonDetailDTO` | Generated sources are missing | `mvn -B generate-sources` |
| Cannot find symbol after editing the spec | Regeneration did not run, or the spec has a YAML error | Re-run; check the generator output for a parse error |
| `getAllValidationResults()` does not exist | Renamed in Spring Framework 7 | Use `getParameterValidationResults()` |
| Testcontainers: connection refused | Docker daemon not running | Start Docker, then `mvn -B verify` |
| Build fails on an "Architecture Violation" | An ArchUnit rule | See [archunit-governance.md](../guides/archunit-governance.md) — move the dependency, don't suppress |
| Coverage gate fails with no new issues of my own | Touched a line inside pre-existing duplicated code | De-duplicate the block you touched |
| Source-hygiene gate fails on a header | A file carries a copyright or licence banner | Delete it. The first line of a `.java` file is its `package` declaration |
| A stale class survives a rebuild | Incremental compile confusion | `mvn -B clean verify` |

## Runtime behaviour

| Symptom | Likely cause | Fix |
|---|---|---|
| First page load is slow | Cold cache — `1 + 2N` upstream calls, so 21 at the default size | Expected once. Seed data plus Redis make it a one-time cost. See [ADR-0006](../adr/0006-redis-cache-pokeapi-fanout.md) |
| A large page is *very* slow | `size=100` means 201 upstream calls on a cold cache | Working as designed; that is why 100 is the cap. Use a smaller page |
| 400 `INVALID_PAGINATION` | `size` above 100, below 1, or negative `page` | Request within `1..100`. We reject rather than clamp, deliberately |
| Every list row has a null category | Only `/pokemon/{id}` was fetched; category lives on `/pokemon-species/{id}` | Fetch both — that is the whole reason for the fan-out |
| Bulbasaur weighs 69 kg | `weight` is hectograms, `height` is decimetres | Use `Mass.toKilograms()` — never divide at a call site |
| Descriptions show odd characters or broken lines | `flavor_text` contains literal `\n` and `\f` | The `Description` VO normalises on construction. Do not bypass it |
| Eevee shows only one evolution | The chain is a recursive tree with 8 branches, not a list | See `EvolutionChainMapperTest` |
| Half my validation failures return 500 | Only one of the two parameter-validation exception types is mapped | Map both `ConstraintViolationException` and `HandlerMethodValidationException` |
| Curator edits vanished after a sync | The merge policy was bypassed | Re-sync must go through `PokemonMergePolicy`. See [ADR-0007](../adr/0007-proprietary-field-merge-policy.md) |
| 412 on an update that looks fine | Optimistic lock — the record changed since you read it | Re-read, reapply, resubmit |
| Batch sync returns 202 with failures | Upstream rate-limited part of the range | Re-run with the returned `failedIds` |
| 403, not 401 | Authenticated but lacking the role. The signature is fine | Check the route's role requirement, not the keys |
| Swagger groups endpoints oddly | `useTags=false` groups by first path segment | Expected; re-tagging does nothing |

## Contract and the consuming repo

| Symptom | Likely cause | Fix |
|---|---|---|
| `make contract-check` fails on `oasdiff` | A breaking change to an existing operation | Add a new versioned path instead of editing in place, or tag a major version |
| AC1c fails — served spec differs from the authored one | Springdoc is generating from annotations instead of serving the file | Point springdoc at the static resource; the authored YAML is the source of truth |
| A consumer compiles but breaks at runtime | It is pinned to an older contract version | Release; the consumer adopts deliberately. Never hand-patch types on either side |
| The SPA gets a CORS error | The dev origin is not in the allow-list | Add it explicitly. Never use `*` |
| A release shipped with no spec asset | The publish stage was skipped or failed | That is a failed release — re-run stage 9 |

## Diagnostics

```bash
docker compose ps                             # who is actually healthy
docker compose logs -f api --tail=100
curl -s localhost:8080/api/actuator/health | jq
docker compose exec redis redis-cli keys 'pokeapi:*' | head
docker compose exec postgres psql -U pokedex -c '\dt'
docker compose down -v && docker compose up --build   # nuclear reset
```

---

**Canonical specs (don't restate — link):**
[quickstart.md](../guides/quickstart.md) · [build and test](../guides/build-and-test.md) · [archunit-governance.md](../guides/archunit-governance.md)

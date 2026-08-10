# Spring Patterns

Spring Boot 4 idioms. Several of these changed in Framework 7 and the failures are
runtime-only.

---

## Constructor injection, and the Boot 4 trap

```java
@Service
public class SyncPokemonUseCase {
    private final PokemonRepository repository;
    private final PokemonCatalog catalog;

    public SyncPokemonUseCase(PokemonRepository repository, PokemonCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }
}
```

Field injection is banned (`java:S6813`). No setter injection. No `@Value` on a field.

> **The regression that will bite you.** Spring Boot 4 / Framework 7 **dropped the implicit
> primary-constructor heuristic**. A bean with two constructors — a production one and a
> test one — now fails at boot with `NoSuchMethodException`, or in a MockMvc context with
> `BeanInstantiationException`. It compiles perfectly. Annotate the production constructor
> with `@Autowired` (`java:S6829`).

## Configuration as records

```java
@ConfigurationProperties("pokeapi")
public record PokeApiProperties(
    URI baseUrl, Duration cacheTtl, int maxConcurrency, Duration connectTimeout
) { }
```

Immutable, validated, discoverable. Never scattered `@Value` annotations. Secrets come from
environment variables and never from `*.properties` (`java:S6437`).

## `RestClient` — not `RestTemplate`, not `WebClient`

```java
@Bean
PokeApiClient pokeApiClient(PokeApiProperties props) {
    return RestClient.builder().baseUrl(props.baseUrl().toString()).build();
}
```

`RestTemplate` is legacy. `WebClient`/WebFlux is rejected platform-wide — the persistence
layer is blocking JPA, so a reactive client bridges back to a pool anyway.

## Transactions at the use case

```java
@Service
@Transactional
public class UpdateLocalPokemonUseCase { … }
```

The use case **is** the transaction boundary — not the controller, not the repository.

- Keep transactions short. **No remote I/O inside one** — an upstream call with a 5-second timeout must not hold a database connection.
- `@Version` optimistic locking by default; a conflict is 412, not a lost update.
- Never call a `@Transactional` method from within the same class — the proxy is bypassed and the annotation silently does nothing (`java:S2229`, `java:S6809`).

## Controllers are thin

```java
@RestController
@RequestMapping("/v1/pokedex")
public class PokemonController implements PokemonApi {
    @Override
    public ResponseEntity<PokemonPageDTO> listPokemon(Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toDto(useCase.list(PageRequest.of(page, size))));
    }
}
```

Bind, delegate, map. Zero business logic, zero `if` on domain state, zero repository access
(ArchUnit `L3`). Every controller implements a generated `*Api` (`OA1`).

## Bean Validation — and the two exception types

Validation constraints come from the OpenAPI spec and land on generated DTOs. Do not
re-declare them by hand.

> **Parameter validation surfaces as two different exceptions**, depending on where the
> constraint sits: `ConstraintViolationException` from the `@Validated` AOP proxy, and
> `HandlerMethodValidationException` from Spring MVC's own method validation. Map **both**
> to the same 400 shape. If you map only one, roughly half your validation failures return
> 500.
>
> Framework 7 also renamed the accessor: `getAllValidationResults()` is gone, use
> `getParameterValidationResults()`.

## Pagination

Every list endpoint takes a `Pageable` (`java:S7186`). Nothing is unbounded.

```properties
spring.data.web.pageable.default-page-size=10
spring.data.web.pageable.max-page-size=100
```

> **Spring silently clamps `size` to `max-page-size` by default.** That is the wrong
> behaviour for us: a client asking for 500 and receiving 100 with no indication has no way
> to know it did not receive everything. We reject instead — `size > 100` is a **400
> `INVALID_PAGINATION`** whose message names the cap. Validate the parameter explicitly
> rather than relying on the clamp.

## Profiles and startup

`dev`, `test`, `prod`. **Fail fast at boot**: validate required configuration when the
context starts rather than on the first request that needs it. A misconfigured service that
starts successfully is worse than one that refuses to.

## Do not use

WebFlux · `RestTemplate` · field injection · scattered `@Value` · hand-rolled security
filters · raw JDBC outside infrastructure · in-memory maps as a cache · leaking entities to
the API · `javax.*` (Jakarta only) · custom actuator replacements.

## Related

[Error handling](error-handling.md) · [Persistence patterns](persistence-patterns.md) · [Java patterns](java-patterns.md)

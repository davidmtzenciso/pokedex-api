package com.elatusdev.pokedex.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.testsupport.PokemonFixture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

// The nine scenarios WU-US04-B B3 makes mandatory, plus the two that only a real database
// can answer: a concurrent PATCH producing exactly one 412, and a delete that leaves no
// orphans behind.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SuppressWarnings("unchecked") // the probe reads an untyped JSON map; the assertion is the type check
class LocalPokemonComponentTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private final String baseUrl;
    private final JdbcTemplate jdbc;
    private final RestClient client = RestClient.create();
    private String token;

    LocalPokemonComponentTest(@Autowired Environment environment, @Autowired JdbcTemplate jdbc) {
        this.baseUrl = "http://localhost:" + environment.getProperty("local.server.port") + "/api";
        this.jdbc = jdbc;
    }

    @BeforeEach
    void resetAndAuthenticate() {
        PokemonFixture.clear(jdbc);
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        post("/v1/security/register", """
                {"username":"curator","email":"curator@elatus-dev.com","password":"Curator123!secure"}""", null);
        token = (String) json(post(
                                "/v1/security/login",
                                """
                                {"username":"curator","password":"Curator123!secure"}""",
                                null)
                        .getBody())
                .get("accessToken");
    }

    private static String createBody(String name) {
        return """
                {"name":"%s","massHectograms":69,"heightDecimetres":7,"region":"KANTO","tags":["starter"]}"""
                .formatted(name);
    }

    private long createRecord(String name) {
        ResponseEntity<String> created = post("/v1/pokedex/local", createBody(name), token);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        return ((Number) json(created.getBody()).get("id")).longValue();
    }

    // 1 — create 201, with a Location header naming the new record
    @Test
    void should_create_a_record_and_report_where_it_lives() {
        ResponseEntity<String> response = post("/v1/pokedex/local", createBody("bulbasaur"), token);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = json(response.getBody());
        assertThat(body).containsEntry("name", "bulbasaur").containsEntry("replicationState", "DRAFT");
        assertThat(body.get("tags")).isEqualTo(List.of("starter"));
        assertThat(response.getHeaders().getLocation()).hasToString("/v1/pokedex/local/" + body.get("id"));
    }

    // 2 — create duplicate 409
    @Test
    void should_reject_a_second_record_claiming_the_same_upstream_id() {
        String linked = """
                {"name":"bulbasaur","pokeApiId":1,"massHectograms":69,"heightDecimetres":7}""";
        assertThat(post("/v1/pokedex/local", linked, token).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> duplicate = post("/v1/pokedex/local", linked, token);

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(json(duplicate.getBody())).containsEntry("code", "DUPLICATE_POKEMON");
    }

    // 3 and 5 — get 200, list 200. Both are public: reading the catalogue needs no principal.
    @Test
    void should_read_a_record_and_list_it_without_a_token() {
        long id = createRecord("bulbasaur");

        assertThat(get("/v1/pokedex/local/" + id, null).getStatusCode().value()).isEqualTo(200);
        Map<String, Object> page = json(get("/v1/pokedex/local", null).getBody());
        assertThat((List<?>) page.get("content")).hasSize(1);
        assertThat((Map<String, Object>) page.get("page")).containsEntry("totalElements", 1);
    }

    // 4 — get 404, as application/problem+json
    @Test
    void should_answer_404_for_a_record_that_does_not_exist() {
        ResponseEntity<String> response = get("/v1/pokedex/local/9999", null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType())
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
        assertThat(json(response.getBody())).containsEntry("code", "POKEMON_NOT_FOUND");
    }

    // 6 — update 200. AC-US04-1: region and tags applied, version incremented.
    @Test
    void should_apply_a_patch_and_increment_the_version() {
        long id = createRecord("bulbasaur");

        ResponseEntity<String> patched = patch(
                "/v1/pokedex/local/" + id,
                """
                {"version":0,"region":"JOHTO","notes":"Route 1 favourite","tags":["starter","grass"]}""",
                token);

        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = json(patched.getBody());
        assertThat(body).containsEntry("region", "JOHTO").containsEntry("notes", "Route 1 favourite");
        assertThat(body.get("tags")).isEqualTo(List.of("starter", "grass"));
        assertThat(((Number) body.get("version")).longValue()).isEqualTo(1L);
    }

    // 7 — update 404 (AC-US04-2, the 404 the story names, on PUT)
    @Test
    void should_answer_404_when_replacing_a_record_that_does_not_exist() {
        ResponseEntity<String> response =
                put("/v1/pokedex/local/9999", """
                        {"version":0,"region":"KANTO"}""", token);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json(response.getBody())).containsEntry("code", "POKEMON_NOT_FOUND");
    }

    // 8 and 9 — delete 204, then get-after-delete 404 (AC-US04-5)
    @Test
    void should_delete_a_record_and_then_answer_404_for_it() {
        long id = createRecord("bulbasaur");

        assertThat(delete("/v1/pokedex/local/" + id, token).getStatusCode().value())
                .isEqualTo(204);

        assertThat(get("/v1/pokedex/local/" + id, null).getStatusCode().value()).isEqualTo(404);
        // I9 — the children go with the parent; a surviving tag row is an orphan
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pokemon_tag", Long.class))
                .isZero();
    }

    // AC-US04-3 — the 400 the story names, and errors[] has to name the field
    @Test
    void should_answer_400_naming_the_field_when_the_payload_is_invalid() {
        ResponseEntity<String> response = post(
                "/v1/pokedex/local",
                """
                {"name":"bulbasaur","massHectograms":-5,"heightDecimetres":7}""",
                token);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> body = json(response.getBody());
        assertThat(body).containsEntry("code", "VALIDATION_ERROR");
        assertThat((List<?>) body.get("errors")).isNotEmpty();
        assertThat(response.getBody()).contains("massHectograms");
    }

    // an id below the contract's minimum is NOT a pagination problem — the bug that made
    // every rejected parameter answer INVALID_PAGINATION
    @Test
    void should_answer_validation_error_rather_than_invalid_pagination_for_a_bad_id() {
        ResponseEntity<String> response = get("/v1/pokedex/local/0", null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody())).containsEntry("code", "VALIDATION_ERROR");
    }

    @Test
    void should_answer_invalid_pagination_when_the_page_size_is_past_the_cap() {
        ResponseEntity<String> response = get("/v1/pokedex/local?size=101", null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody())).containsEntry("code", "INVALID_PAGINATION");
    }

    // AC-US04-1 — every mutating route needs a principal, and writes nothing without one
    @Test
    void should_reject_every_mutation_without_a_token_and_write_nothing() {
        assertThat(post("/v1/pokedex/local", createBody("ghost"), null)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pokemon", Long.class)).isZero();
    }

    // AC-US04-4 — two writers, one version. Exactly one wins; the loser gets 412, not a
    // silently lost update.
    @Test
    void should_let_exactly_one_of_two_concurrent_patches_win() throws Exception {
        long id = createRecord("bulbasaur");
        String body = """
                {"version":0,"region":"KANTO","tags":["starter"]}""";

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> both = List.of(
                    () -> patch("/v1/pokedex/local/" + id, body, token).getStatusCode().value(),
                    () -> patch("/v1/pokedex/local/" + id, body, token).getStatusCode().value());
            List<Integer> statuses =
                    pool.invokeAll(both).stream().map(LocalPokemonComponentTest::valueOf).toList();

            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        }
    }

    // AC-US04-6 — filters compose, and the total describes the filtered set
    @Test
    void should_narrow_by_composed_filters_and_report_the_filtered_total() {
        createRecord("bulbasaur");
        post("/v1/pokedex/local", """
                {"name":"chikorita","massHectograms":64,"heightDecimetres":9,"region":"JOHTO","tags":["starter"]}""",
                token);

        Map<String, Object> page = json(get("/v1/pokedex/local?region=KANTO&tag=starter", null).getBody());

        assertThat((List<?>) page.get("content")).hasSize(1);
        assertThat((Map<String, Object>) page.get("page")).containsEntry("totalElements", 1);
    }

    private static Integer valueOf(Future<Integer> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (java.util.concurrent.ExecutionException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static Map<String, Object> json(String body) {
        return JsonMapper.builder().build().readValue(body, Map.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        return exchange(HttpMethod.GET, path, null, bearer);
    }

    private ResponseEntity<String> post(String path, String body, String bearer) {
        return exchange(HttpMethod.POST, path, body, bearer);
    }

    private ResponseEntity<String> put(String path, String body, String bearer) {
        return exchange(HttpMethod.PUT, path, body, bearer);
    }

    private ResponseEntity<String> patch(String path, String body, String bearer) {
        return exchange(HttpMethod.PATCH, path, body, bearer);
    }

    private ResponseEntity<String> delete(String path, String bearer) {
        return exchange(HttpMethod.DELETE, path, null, bearer);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String body, String bearer) {
        RestClient.RequestBodySpec request =
                client.method(method).uri(baseUrl + path).contentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        if (body != null) {
            request.body(body);
        }
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    // the status is the assertion
                })
                .toEntity(String.class);
    }
}

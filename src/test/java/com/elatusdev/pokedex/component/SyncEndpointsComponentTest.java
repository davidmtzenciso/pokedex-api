package com.elatusdev.pokedex.component;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.testsupport.PokemonFixture;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
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
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

// The sync endpoints had no component test: SyncController was reached only as a 401 check
// inside AuthEnforcementComponentTest. The use cases beneath it were well covered, so what
// was untested was precisely the HTTP layer's own decisions — 201 against 200, and the 202
// that says "accepted and partially complete" rather than "all of it worked".
//
// AC5 is the reason this file matters most. "Re-sync preserves every proprietary field" is
// the headline claim of WF-US03, and until now it was only ever asserted against the merge
// policy in isolation, never through the endpoint a curator actually calls.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SuppressWarnings("unchecked") // JSON comes back untyped; the assertions are the type check
class SyncEndpointsComponentTest {

    private static final WireMockServer UPSTREAM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private final String baseUrl;
    private final JdbcTemplate jdbc;
    private final RestClient client = RestClient.create();
    private String token;

    SyncEndpointsComponentTest(@Autowired Environment environment, @Autowired JdbcTemplate jdbc) {
        this.baseUrl = "http://localhost:" + environment.getProperty("local.server.port") + "/api";
        this.jdbc = jdbc;
    }

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        UPSTREAM.start();
        REDIS.start();
        registry.add("pokeapi.base-url", () -> "http://localhost:" + UPSTREAM.port());
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopEverything() {
        UPSTREAM.stop();
        REDIS.stop();
    }

    @BeforeEach
    void resetAndAuthenticate() {
        UPSTREAM.resetAll();
        flushCache();
        stubUpstream();
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

    // --- the status decisions the controller owns -------------------------------------

    @Test
    void should_answer_201_and_name_the_new_record_when_it_is_replicated_for_the_first_time() {
        ResponseEntity<String> response = post("/v1/pokedex/sync/1", "", token);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = json(response.getBody());
        assertThat(body).containsEntry("pokeApiId", 1);
        assertThat(response.getHeaders().getLocation()).hasToString("/v1/pokedex/local/" + body.get("id"));
    }

    // A freshly replicated record is SYNCED, and re-syncing it is refused rather than
    // re-fetched: upstream is fair-use rate limited (IA10), so a request that would change
    // nothing must not cost an upstream call. The 409 is the guard working, not a defect.
    @Test
    void should_refuse_to_re_fetch_a_record_that_was_just_replicated() {
        assertThat(post("/v1/pokedex/sync/1", "", token).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> immediate = post("/v1/pokedex/sync/1", "", token);

        assertThat(immediate.getStatusCode().value()).isEqualTo(409);
        assertThat(json(immediate.getBody())).containsEntry("code", "ILLEGAL_STATE_TRANSITION");
    }

    // 200, not another 201: the record already existed and was refreshed, and a 201 would
    // tell the caller something was created that was not
    @Test
    void should_answer_200_when_a_stale_record_is_refreshed() {
        assertThat(post("/v1/pokedex/sync/1", "", token).getStatusCode().value()).isEqualTo(201);
        backdateSyncedAt();

        ResponseEntity<String> refreshed = post("/v1/pokedex/sync/1", "", token);

        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        assertThat(refreshed.getHeaders().getLocation()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pokemon", Long.class)).isEqualTo(1L);
    }

    // AC-US03-4 — 202, because the batch is accepted and partially complete. 200 would
    // claim all of it worked while the summary in the body said otherwise.
    @Test
    void should_answer_202_with_a_summary_when_a_batch_is_accepted() {
        ResponseEntity<String> response =
                post("/v1/pokedex/sync/batch", """
                        {"from":1,"to":3}""", token);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        Map<String, Object> summary = json(response.getBody());
        assertThat(summary).containsKeys("succeeded", "failed", "skipped", "failedIds");
        assertThat(((Number) summary.get("succeeded")).intValue()).isEqualTo(3);
    }

    @Test
    void should_report_the_ids_it_could_not_replicate_rather_than_failing_the_batch() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/2")).willReturn(aResponse().withStatus(404)));

        ResponseEntity<String> response =
                post("/v1/pokedex/sync/batch", """
                        {"from":1,"to":3}""", token);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        Map<String, Object> summary = json(response.getBody());
        assertThat(((Number) summary.get("succeeded")).intValue()).isEqualTo(2);
        assertThat(((Number) summary.get("failed")).intValue()).isEqualTo(1);
        assertThat((List<Object>) summary.get("failedIds")).containsExactly(2);
    }

    // --- the claim the whole story rests on --------------------------------------------

    // AC5 / F7 — a re-sync against changed upstream data updates every replicated field and
    // leaves every proprietary field byte-identical. Asserted here through the endpoints a
    // curator actually uses, not against the merge policy in isolation.
    @Test
    void should_preserve_every_curated_field_when_upstream_data_changes_underneath_it() {
        long id = ((Number) json(post("/v1/pokedex/sync/1", "", token).getBody()).get("id")).longValue();
        patch(
                "/v1/pokedex/local/" + id,
                """
                {"version":0,"region":"KANTO","notes":"Route 1 favourite","tags":["starter","grass"]}""",
                token);

        backdateSyncedAt();
        flushCache();

        // upstream changes the replicated half underneath the curated record
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/1"))
                .willReturn(jsonResponse(fixture("pokemon-1.json").replace("\"base_experience\": 64", "\"base_experience\": 99"))));

        ResponseEntity<String> resynced = post("/v1/pokedex/sync/1", "", token);

        assertThat(resynced.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = json(resynced.getBody());
        // replicated: taken from upstream
        assertThat(((Number) body.get("baseExperience")).intValue()).isEqualTo(99);
        // proprietary: untouched, every one of them
        assertThat(body).containsEntry("region", "KANTO").containsEntry("notes", "Route 1 favourite");
        assertThat(body.get("tags")).isEqualTo(List.of("starter", "grass"));
        assertThat(body).containsEntry("replicationState", "CUSTOMIZED");
    }

    // --- the error rows -----------------------------------------------------------------

    @Test
    void should_answer_404_when_upstream_has_no_such_pokemon() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/999")).willReturn(aResponse().withStatus(404)));
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/missingno")).willReturn(aResponse().withStatus(404)));

        ResponseEntity<String> response = post("/v1/pokedex/sync/missingno", "", token);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType())
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
    }

    // AC-AUTH-1 — a sync costs upstream calls and writes rows, so it must be attributable
    @Test
    void should_reject_an_unauthenticated_sync_and_write_nothing() {
        assertThat(post("/v1/pokedex/sync/1", "", null).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/v1/pokedex/sync/batch", """
                {"from":1,"to":3}""", null)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pokemon", Long.class)).isZero();
        assertThat(UPSTREAM.getAllServeEvents()).isEmpty();
    }

    // --- helpers -------------------------------------------------------------------------

    // The upstream cache outlives a test, so without this a 404 stub is never reached for
    // an id an earlier test already cached — and the batch reports three successes.
    //
    // Only the pokeapi: keys go. FLUSHALL would take the jti sessions with them, because the
    // cache and the session store share one Redis — and the caller would be silently logged
    // out mid-test. Worth knowing outside the tests too: flushing that cache in production
    // signs everybody out.
    private void flushCache() {
        try {
            REDIS.execInContainer(
                    "sh", "-c", "redis-cli --scan --pattern 'pokeapi:*' | xargs -r redis-cli del");
        } catch (IOException | InterruptedException failed) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("could not flush the cache between tests", failed);
        }
    }

    // re-sync is guarded: only a STALE or FAILED record may be refreshed, and staleness is
    // 24 hours. Backdating synced_at is how a test reaches the contract's 200 without
    // waiting a day, and without weakening the guard that protects a rate-limited upstream.
    private void backdateSyncedAt() {
        jdbc.update("UPDATE pokemon SET synced_at = now() - interval '2 days'");
    }

    private void stubUpstream() {
        for (int id = 1; id <= 3; id++) {
            UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/" + id))
                    .willReturn(jsonResponse(fixture("pokemon-1.json")
                            .replace("\"id\": 1,", "\"id\": " + id + ",")
                            .replace("pokemon-species/1/", "pokemon-species/" + id + "/"))));
            UPSTREAM.stubFor(
                    get(urlPathEqualTo("/pokemon-species/" + id)).willReturn(jsonResponse(fixture("species-1.json"))));
        }
        UPSTREAM.stubFor(get(urlPathEqualTo("/evolution-chain/1")).willReturn(jsonResponse(fixture("evolution-chain-1.json"))));
    }

    private static String fixture(String name) {
        try (InputStream in = SyncEndpointsComponentTest.class.getResourceAsStream("/pokeapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static Map<String, Object> json(String body) {
        return JsonMapper.builder().build().readValue(body, Map.class);
    }

    private ResponseEntity<String> post(String path, String body, String bearer) {
        return exchange(HttpMethod.POST, path, body, bearer);
    }

    private ResponseEntity<String> patch(String path, String body, String bearer) {
        return exchange(HttpMethod.PATCH, path, body, bearer);
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

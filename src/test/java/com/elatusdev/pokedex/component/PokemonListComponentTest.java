package com.elatusdev.pokedex.component;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.web.dto.PokemonPageDTO;
import com.elatusdev.pokedex.web.dto.ProblemDetailDTO;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

// The story, not the plumbing. Every assertion here maps to an acceptance criterion.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PokemonListComponentTest {

    private static final WireMockServer UPSTREAM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private final String baseUrl;

    PokemonListComponentTest(@Autowired Environment environment) {
        this.baseUrl = "http://localhost:" + environment.getProperty("local.server.port") + "/api";
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
    static void stopUpstream() {
        UPSTREAM.stop();
        REDIS.stop();
    }

    @BeforeEach
    void resetUpstream() {
        UPSTREAM.resetAll();
        flushCache();
    }

    private void flushCache() {
        try {
            REDIS.execInContainer("redis-cli", "FLUSHALL");
        } catch (IOException | InterruptedException failed) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("could not flush the cache between tests", failed);
        }
    }

    private static String fixture(String name) {
        try (InputStream in = PokemonListComponentTest.class.getResourceAsStream("/pokeapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void stubCatalogue() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon")).willReturn(json(fixture("pokemon-list.json"))));
        for (int id = 1; id <= 10; id++) {
            UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/" + id))
                    .willReturn(json(fixture("pokemon-1.json")
                            .replace("\"id\": 1,", "\"id\": " + id + ",")
                            .replace("pokemon-species/1/", "pokemon-species/" + id + "/"))));
            UPSTREAM.stubFor(
                    get(urlPathEqualTo("/pokemon-species/" + id)).willReturn(json(fixture("species-1.json"))));
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private <T> ResponseEntity<T> getPage(String query, Class<T> type) {
        return RestClient.create()
                .get()
                .uri(baseUrl + "/v1/pokedex/pokemon" + query)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // the status is under test; the default handler would throw first
                })
                .toEntity(type);
    }

    // AC-US01-1 — a default page is ten rows, and every one is complete. "Complete" is the
    // story's actual requirement: sprite, category, mass in kilograms, and abilities.
    @Test
    void should_return_ten_complete_rows_when_no_parameters_are_given() {
        stubCatalogue();

        ResponseEntity<PokemonPageDTO> response = getPage("", PokemonPageDTO.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        PokemonPageDTO page = response.getBody();
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getPage().getSize()).isEqualTo(10);
        assertThat(page.getPage().getTotalElements()).isEqualTo(1351L);
        assertThat(page.getContent()).allSatisfy(row -> {
            assertThat(row.getSprite().getOfficialArtwork()).hasToString(
                    "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png");
            assertThat(row.getCategory()).isEqualTo("Seed Pokémon");
            assertThat(row.getMassKilograms()).isEqualByComparingTo("6.9");
            assertThat(row.getAbilities()).isNotEmpty();
            assertThat(row.getStale()).isFalse();
        });
    }

    // AC-US01-2 — rejected, never clamped
    @Test
    void should_reject_a_size_above_the_cap_as_a_problem_detail() {
        stubCatalogue();

        ResponseEntity<ProblemDetailDTO> response = getPage("?size=101", ProblemDetailDTO.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAGINATION");
        assertThat(response.getBody().getDetail()).contains("100");
        assertThat(UPSTREAM.getAllServeEvents()).isEmpty();
    }

    @Test
    void should_reject_a_size_below_one() {
        assertThat(getPage("?size=0", ProblemDetailDTO.class).getBody().getCode()).isEqualTo("INVALID_PAGINATION");
    }

    @Test
    void should_reject_a_negative_page() {
        assertThat(getPage("?page=-1", ProblemDetailDTO.class).getBody().getCode()).isEqualTo("INVALID_PAGINATION");
    }

    @Test
    void should_accept_the_maximum_size() {
        stubCatalogue();

        assertThat(getPage("?size=100", PokemonPageDTO.class).getStatusCode().value()).isEqualTo(200);
    }

    // AC-US01-3 and AC-US01-4 — 1 + 2N cold, zero warm
    @Test
    void should_issue_twenty_one_calls_cold_and_none_warm() {
        stubCatalogue();

        getPage("", PokemonPageDTO.class);
        assertThat(UPSTREAM.getAllServeEvents()).hasSize(21);
        UPSTREAM.resetRequests();

        ResponseEntity<PokemonPageDTO> warm = getPage("", PokemonPageDTO.class);

        assertThat(UPSTREAM.getAllServeEvents()).isEmpty();
        assertThat(warm.getBody().getContent()).hasSize(10);
    }

    // AC-US01-6 — an outage with nothing local to serve is a 502, never a 500: it is not
    // our failure, and the status is how a caller tells "retry later" from "you were wrong"
    @Test
    void should_return_502_when_upstream_is_unavailable_and_no_replica_exists() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon")).willReturn(aResponse().withStatus(500)));
        UPSTREAM.stubFor(get(urlPathMatching("/pokemon/\\d+")).willReturn(aResponse().withStatus(500)));

        ResponseEntity<ProblemDetailDTO> response = getPage("", ProblemDetailDTO.class);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getCode()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }
}

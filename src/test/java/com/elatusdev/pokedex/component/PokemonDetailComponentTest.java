package com.elatusdev.pokedex.component;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.contract.dto.NameSourceDTO;
import com.elatusdev.pokedex.contract.dto.PokemonDetailDTO;
import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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

// AC-US02-1 … AC-US02-4. Eevee is the fixture because a mapper that loops over two levels
// passes for Bulbasaur and silently truncates every branching family.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PokemonDetailComponentTest {

    private static final WireMockServer UPSTREAM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private final String baseUrl;

    PokemonDetailComponentTest(@Autowired Environment environment) {
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
        try {
            REDIS.execInContainer("redis-cli", "FLUSHALL");
        } catch (IOException | InterruptedException failed) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("could not flush the cache between tests", failed);
        }
    }

    private static String fixture(String name) {
        try (InputStream in = PokemonDetailComponentTest.class.getResourceAsStream("/pokeapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private void stubBulbasaur() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/1")).willReturn(json(fixture("pokemon-1.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/bulbasaur")).willReturn(json(fixture("pokemon-1.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon-species/1")).willReturn(json(fixture("species-1.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/evolution-chain/1"))
                .willReturn(json(fixture("evolution-chain-1.json"))));
    }

    private void stubEevee() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/eevee")).willReturn(json(fixture("pokemon-133.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon-species/133")).willReturn(json(fixture("species-133.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/evolution-chain/67"))
                .willReturn(json(fixture("evolution-chain-67.json"))));
    }

    private <T> ResponseEntity<T> getDetail(String idOrName, Class<T> type) {
        return RestClient.create()
                .get()
                .uri(baseUrl + "/v1/pokedex/pokemon/" + idOrName)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // the status is under test
                })
                .toEntity(type);
    }

    // AC-US02-1 — the full record: artwork, six stats, a clean description
    @Test
    void should_return_the_full_record_when_asked_by_id() {
        stubBulbasaur();

        ResponseEntity<PokemonDetailDTO> response = getDetail("1", PokemonDetailDTO.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        PokemonDetailDTO detail = response.getBody();
        assertThat(detail.getPokeApiId()).isEqualTo(1);
        assertThat(detail.getName()).isEqualTo("bulbasaur");
        assertThat(detail.getCategory()).isEqualTo("Seed Pokémon");
        assertThat(detail.getSprite().getOfficialArtwork()).hasToString(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png");
        assertThat(detail.getStats()).hasSize(6);
        assertThat(detail.getTypes()).hasSize(2);
        assertThat(detail.getBaseExperience()).isEqualTo(64);
        assertThat(detail.getStale()).isFalse();
    }

    // AC-US02-2 — units. Upstream reports hectograms and decimetres.
    @Test
    void should_render_mass_in_kilograms_and_height_in_metres() {
        stubBulbasaur();

        PokemonDetailDTO detail = getDetail("1", PokemonDetailDTO.class).getBody();

        assertThat(detail.getMassKilograms()).isEqualByComparingTo("6.9");
        assertThat(detail.getHeightMetres()).isEqualByComparingTo("0.7");
    }

    // AC-US02-3 — the description arrives with literal \n and \f embedded upstream
    @Test
    void should_return_a_description_free_of_upstream_control_characters() {
        stubBulbasaur();

        PokemonDetailDTO detail = getDetail("1", PokemonDetailDTO.class).getBody();

        assertThat(detail.getDescription())
                .doesNotContain("\n")
                .doesNotContain("\f")
                .startsWith("A strange seed was planted");
    }

    @Test
    void should_accept_a_name_as_well_as_an_id() {
        stubBulbasaur();

        assertThat(getDetail("bulbasaur", PokemonDetailDTO.class).getBody().getName()).isEqualTo("bulbasaur");
    }

    @Test
    void should_seed_the_localized_names_from_upstream() {
        stubBulbasaur();

        PokemonDetailDTO detail = getDetail("1", PokemonDetailDTO.class).getBody();

        assertThat(detail.getLocalizedNames()).hasSize(12);
        assertThat(detail.getLocalizedNames())
                .allSatisfy(name -> assertThat(name.getSource()).isEqualTo(NameSourceDTO.UPSTREAM));
    }

    // AC-US02-4 — eight branches through the API, not only through the mapper
    @Test
    void should_return_all_eight_branches_when_the_pokemon_is_eevee() {
        stubEevee();

        PokemonDetailDTO detail = getDetail("eevee", PokemonDetailDTO.class).getBody();

        assertThat(detail.getEvolution()).hasSize(8);
        assertThat(detail.getEvolution())
                .allSatisfy(edge -> assertThat(edge.getFrom()).isEqualTo(133));
        assertThat(detail.getEvolution().stream().map(edge -> edge.getTo()))
                .containsExactlyInAnyOrder(134, 135, 136, 196, 197, 470, 471, 700);
    }

    @Test
    void should_return_an_empty_evolution_list_when_the_species_never_evolves() {
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon/1")).willReturn(json(fixture("pokemon-1.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/pokemon-species/1")).willReturn(json(fixture("species-1.json"))));
        UPSTREAM.stubFor(get(urlPathEqualTo("/evolution-chain/1"))
                .willReturn(json(
                        """
                        {"id":1,"chain":{"species":{"name":"bulbasaur",
                         "url":"https://pokeapi.co/api/v2/pokemon-species/1/"},
                         "evolution_details":[],"evolves_to":[]}}
                        """)));

        PokemonDetailDTO detail = getDetail("1", PokemonDetailDTO.class).getBody();

        assertThat(detail.getEvolution()).isEmpty();
    }

    @Test
    void should_return_404_as_problem_json_when_upstream_has_no_such_pokemon() {
        UPSTREAM.stubFor(get(urlPathMatching("/pokemon/.*")).willReturn(aResponse().withStatus(404)));

        ResponseEntity<ProblemDetailDTO> response = getDetail("missingno", ProblemDetailDTO.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getCode()).isEqualTo("POKEMON_NOT_FOUND_UPSTREAM");
        assertThat(response.getBody().getDetail()).contains("missingno");
    }

    @Test
    void should_return_502_when_upstream_fails_and_no_replica_exists() {
        UPSTREAM.stubFor(get(urlPathMatching("/pokemon/.*")).willReturn(aResponse().withStatus(500)));

        ResponseEntity<ProblemDetailDTO> response = getDetail("1", ProblemDetailDTO.class);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().getCode()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }
}

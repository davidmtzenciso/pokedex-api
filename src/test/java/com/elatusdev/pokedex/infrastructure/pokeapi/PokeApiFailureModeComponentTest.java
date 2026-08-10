package com.elatusdev.pokedex.infrastructure.pokeapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.UpstreamTimeoutException;
import com.elatusdev.pokedex.domain.exception.UpstreamUnavailableException;
import com.elatusdev.pokedex.infrastructure.cache.InMemoryCachePort;
import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.port.CatalogPage;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Every row of the upstream failure matrix has a defined outcome, and each one occurs in
// production. Testing only the happy path here is the whole reason WU-US01-A calls this out.
class PokeApiFailureModeComponentTest {

    private WireMockServer upstream;
    private PokeApiCatalogAdapter adapter;
    private InMemoryCachePort cache;

    @BeforeEach
    void startUpstream() {
        upstream = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        upstream.start();
        PokeApiProperties properties = new PokeApiProperties(
                URI.create("http://localhost:" + upstream.port()),
                Duration.ofMillis(500),
                Duration.ofMillis(300),
                2,
                Duration.ofMillis(5),
                8,
                Duration.ofHours(24),
                50);
        cache = new InMemoryCachePort();
        adapter = new PokeApiCatalogAdapter(
                new PokeApiClient(properties), new PokeApiMapper(), new EvolutionChainMapper(), properties, cache);
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop();
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private void stubHappyPage() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon")).willReturn(json(PokeApiFixtures.raw("pokemon-list.json"))));
        upstream.stubFor(
                get(urlPathMatching("/pokemon/\\d+")).willReturn(json(PokeApiFixtures.raw("pokemon-1.json"))));
        upstream.stubFor(get(urlPathMatching("/pokemon-species/\\d+"))
                .willReturn(json(PokeApiFixtures.raw("species-1.json"))));
    }

    @Test
    void should_map_a_real_recorded_payload_into_a_complete_row() {
        stubHappyPage();

        List<Pokemon> rows = adapter.fetchPage(0, 10).rows();

        assertThat(rows).hasSize(10);
        assertThat(rows.getFirst().replicated().category()).isPresent();
        assertThat(rows.getFirst().replicated().abilities()).isNotEmpty();
        assertThat(rows.getFirst().replicated().mass().toKilograms()).isEqualByComparingTo("6.9");
        assertThat(rows.getFirst().replicated().sprite().preferred()).isPresent();
    }

    // IA5 end to end: the branching chain has to survive the adapter, not only the mapper
    @Test
    void should_carry_all_eight_branches_when_the_detail_is_eevee() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/133")).willReturn(json(PokeApiFixtures.raw("pokemon-133.json"))));
        upstream.stubFor(get(urlPathEqualTo("/pokemon-species/133"))
                .willReturn(json(PokeApiFixtures.raw("species-133.json"))));
        upstream.stubFor(get(urlPathEqualTo("/evolution-chain/67"))
                .willReturn(json(PokeApiFixtures.raw("evolution-chain-67.json"))));

        assertThat(adapter.fetchById(PokeApiId.of(133)))
                .isPresent()
                .get()
                .satisfies(eevee -> assertThat(eevee.replicated().evolutionLinks()).hasSize(8));
    }

    // WF-US02 §9.5: fail loudly, never a Japanese fallback. On a page that means the row is
    // dropped rather than silently rendered in the wrong language.
    @Test
    void should_drop_a_row_whose_species_carries_only_non_english_genera() {
        stubHappyPage();
        upstream.stubFor(get(urlPathEqualTo("/pokemon-species/1"))
                .willReturn(json(
                        """
                        {"id":1,"name":"bulbasaur",
                         "genera":[{"genus":"たねポケモン","language":{"name":"ja-hrkt","url":"x/1/"}}],
                         "flavor_text_entries":[],"names":[],
                         "evolution_chain":{"url":"https://pokeapi.co/api/v2/evolution-chain/1/"}}
                        """)));

        assertThat(adapter.fetchPage(0, 10).rows()).isEmpty();
    }

    @Test
    void should_return_empty_when_the_detail_is_absent_upstream() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/99999")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter.fetchById(PokeApiId.of(99999))).isEmpty();
    }

    @Test
    void should_raise_upstream_unavailable_when_the_detail_call_returns_500() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/1")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.fetchById(PokeApiId.of(1)))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void should_raise_upstream_timeout_when_the_detail_call_exceeds_the_read_timeout() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/1"))
                .willReturn(json("{}").withFixedDelay(1200)));

        assertThatThrownBy(() -> adapter.fetchById(PokeApiId.of(1))).isInstanceOf(UpstreamTimeoutException.class);
    }

    @Test
    void should_raise_upstream_unavailable_rather_than_a_null_pointer_when_the_payload_is_malformed() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/1")).willReturn(json("{\"id\": 1, \"abilities\": [")));

        assertThatThrownBy(() -> adapter.fetchById(PokeApiId.of(1)))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    // IA10 — PokeAPI is fair-use rate limited. A 429 is transient, so it is retried with
    // backoff rather than treated as a permanent client error.
    @Test
    void should_retry_a_rate_limited_call_before_giving_up() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/1")).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> adapter.fetchById(PokeApiId.of(1)))
                .isInstanceOf(UpstreamUnavailableException.class);

        upstream.verify(3, getRequestedFor(urlPathEqualTo("/pokemon/1")));
    }

    @Test
    void should_keep_the_page_when_a_single_row_times_out() {
        stubHappyPage();
        upstream.stubFor(get(urlPathEqualTo("/pokemon/7")).willReturn(json("{}").withFixedDelay(1200)));

        assertThat(adapter.fetchPage(0, 10).rows()).hasSize(9);
    }
}

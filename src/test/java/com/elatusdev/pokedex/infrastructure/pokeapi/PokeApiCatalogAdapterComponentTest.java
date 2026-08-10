// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.infrastructure.pokeapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.UpstreamUnavailableException;
import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.elatusdev.pokedex.domain.vo.PokemonName;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PokeApiCatalogAdapterComponentTest {

    private static final int MAX_CONCURRENCY = 4;

    private WireMockServer upstream;
    private InFlightProbe probe;
    private PokeApiCatalogAdapter adapter;

    @BeforeEach
    void startUpstream() {
        probe = new InFlightProbe();
        upstream = new WireMockServer(WireMockConfiguration.options().dynamicPort().extensions(probe));
        upstream.start();
        PokeApiProperties properties = new PokeApiProperties(
                URI.create("http://localhost:" + upstream.port()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                0,
                Duration.ofMillis(1),
                MAX_CONCURRENCY,
                Duration.ofHours(24),
                50);
        adapter = new PokeApiCatalogAdapter(
                new PokeApiClient(properties), new PokeApiMapper(), new EvolutionChainMapper(), properties);
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop();
    }

    private void stubCatalogue() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon")).willReturn(json(PokeApiFixtures.raw("pokemon-list.json"))));
        upstream.stubFor(
                get(urlPathMatching("/pokemon/\\d+")).willReturn(json(PokeApiFixtures.raw("pokemon-1.json"))));
        upstream.stubFor(get(urlPathMatching("/pokemon-species/\\d+"))
                .willReturn(json(PokeApiFixtures.raw("species-1.json"))));
        upstream.stubFor(get(urlPathMatching("/evolution-chain/\\d+"))
                .willReturn(json(PokeApiFixtures.raw("evolution-chain-1.json"))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    // IA1 — the list endpoint carries only {name, url}, so a page of N costs 1 + 2N.
    // 21 calls at the default size is the number the cache exists to remove.
    @Test
    void should_issue_one_plus_two_n_upstream_calls_for_a_cold_page() {
        stubCatalogue();

        List<Pokemon> rows = adapter.fetchPage(0, 10);

        assertThat(rows).hasSize(10);
        assertThat(upstream.getAllServeEvents()).hasSize(21);
        upstream.verify(1, getRequestedFor(urlPathEqualTo("/pokemon")));
        upstream.verify(10, getRequestedFor(urlPathMatching("/pokemon/\\d+")));
        upstream.verify(10, getRequestedFor(urlPathMatching("/pokemon-species/\\d+")));
    }

    // virtual threads make it easy to issue 3000 concurrent requests against a fair-use
    // API; the semaphore is what stops a page becoming a self-inflicted outage (IA10)
    @Test
    void should_never_exceed_the_configured_concurrency() {
        stubCatalogue();

        adapter.fetchPage(0, 10);

        assertThat(probe.peak()).isEqualTo(MAX_CONCURRENCY);
    }

    @Test
    void should_request_the_page_window_upstream() {
        stubCatalogue();

        adapter.fetchPage(3, 10);

        upstream.verify(getRequestedFor(urlPathEqualTo("/pokemon"))
                .withQueryParam("offset", com.github.tomakehurst.wiremock.client.WireMock.equalTo("30"))
                .withQueryParam("limit", com.github.tomakehurst.wiremock.client.WireMock.equalTo("10")));
    }

    @Test
    void should_report_the_total_upstream_count() {
        stubCatalogue();

        assertThat(adapter.totalCount()).isEqualTo(1351);
    }

    // one failing row must not fail the page — docs/handbook/concurrency.md
    @Test
    void should_drop_a_row_and_keep_the_page_when_one_detail_call_fails() {
        stubCatalogue();
        upstream.stubFor(get(urlPathEqualTo("/pokemon/4")).willReturn(aResponse().withStatus(500)));

        List<Pokemon> rows = adapter.fetchPage(0, 10);

        assertThat(rows).hasSize(9);
    }

    @Test
    void should_fetch_one_pokemon_with_its_evolution_chain_by_id() {
        stubCatalogue();

        Optional<Pokemon> found = adapter.fetchById(PokeApiId.of(1));

        assertThat(found).isPresent();
        assertThat(found.get().replicated().name()).isEqualTo(new PokemonName("bulbasaur"));
        assertThat(found.get().replicated().evolutionLinks()).hasSize(2);
        upstream.verify(1, getRequestedFor(urlPathMatching("/evolution-chain/\\d+")));
    }

    @Test
    void should_fetch_one_pokemon_by_name() {
        stubCatalogue();
        upstream.stubFor(get(urlPathEqualTo("/pokemon/bulbasaur"))
                .willReturn(json(PokeApiFixtures.raw("pokemon-1.json"))));

        assertThat(adapter.fetchByName(new PokemonName("bulbasaur")))
                .isPresent()
                .get()
                .satisfies(pokemon -> assertThat(pokemon.pokeApiId()).contains(PokeApiId.of(1)));
    }

    @Test
    void should_return_empty_when_upstream_has_no_such_pokemon() {
        upstream.stubFor(get(urlPathMatching("/pokemon/\\d+")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter.fetchById(PokeApiId.of(99999))).isEmpty();
    }

    @Test
    void should_propagate_upstream_unavailable_when_the_list_call_fails() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.fetchPage(0, 10)).isInstanceOf(UpstreamUnavailableException.class);
    }

    // Holds each fan-out request until MAX_CONCURRENCY of them are being served at once, so
    // the peak is observed rather than inferred from timing. If the semaphore let more
    // through the peak would exceed the bound; if it let fewer, the latch times out and the
    // peak falls short. The list call is excluded — it runs alone, before the fan-out.
    private static final class InFlightProbe extends ResponseTransformer {

        private final CountDownLatch gate = new CountDownLatch(MAX_CONCURRENCY);
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            if (request.getUrl().startsWith("/pokemon?")) {
                return response;
            }
            int now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            gate.countDown();
            try {
                gate.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return response;
        }

        @Override
        public String getName() {
            return "in-flight-probe";
        }

        @Override
        public boolean applyGlobally() {
            return true;
        }

        int peak() {
            return peak.get();
        }
    }
}

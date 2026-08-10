// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.infrastructure.pokeapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.exception.UpstreamTimeoutException;
import com.elatusdev.pokedex.domain.exception.UpstreamUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PokeApiClientComponentTest {

    private WireMockServer upstream;
    private PokeApiClient client;

    @BeforeEach
    void startUpstream() {
        upstream = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        upstream.start();
        client = new PokeApiClient(properties(upstream.port()));
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop();
    }

    // short timeouts and a near-zero backoff: the policy under test is the shape of the
    // retry, not the wall-clock the production values imply
    private static PokeApiProperties properties(int port) {
        return new PokeApiProperties(
                URI.create("http://localhost:" + port),
                Duration.ofMillis(500),
                Duration.ofMillis(300),
                3,
                Duration.ofMillis(10),
                16,
                Duration.ofHours(24),
                5);
    }

    @Test
    void should_return_the_body_when_upstream_answers_200() {
        upstream.stubFor(get(urlEqualTo("/pokemon/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"bulbasaur\"}")));

        Optional<PokeApiNameRef> body = client.get("/pokemon/1", PokeApiNameRef.class);

        assertThat(body).map(PokeApiNameRef::name).contains("bulbasaur");
        upstream.verify(1, getRequestedFor(urlEqualTo("/pokemon/1")));
    }

    @Test
    void should_return_empty_when_upstream_answers_404() {
        upstream.stubFor(get(urlEqualTo("/pokemon/99999")).willReturn(aResponse().withStatus(404)));

        assertThat(client.get("/pokemon/99999", PokeApiNameRef.class)).isEmpty();
    }

    // a 404 will never become a 200; three retries turn one wasted call into four
    @Test
    void should_not_retry_when_upstream_answers_404() {
        upstream.stubFor(get(urlEqualTo("/pokemon/99999")).willReturn(aResponse().withStatus(404)));

        client.get("/pokemon/99999", PokeApiNameRef.class);

        upstream.verify(1, getRequestedFor(urlEqualTo("/pokemon/99999")));
    }

    @Test
    void should_retry_then_fail_when_upstream_answers_500() {
        upstream.stubFor(get(urlEqualTo("/pokemon/1")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.get("/pokemon/1", PokeApiNameRef.class))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("/pokemon/1");

        upstream.verify(4, getRequestedFor(urlEqualTo("/pokemon/1")));
    }

    @Test
    void should_succeed_when_a_retry_recovers_from_a_transient_failure() {
        upstream.stubFor(get(urlEqualTo("/pokemon/1"))
                .inScenario("flaky")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        upstream.stubFor(get(urlEqualTo("/pokemon/1"))
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"bulbasaur\"}")));

        assertThat(client.get("/pokemon/1", PokeApiNameRef.class))
                .map(PokeApiNameRef::name)
                .contains("bulbasaur");

        upstream.verify(2, getRequestedFor(urlEqualTo("/pokemon/1")));
    }

    @Test
    void should_raise_upstream_timeout_when_the_read_timeout_elapses() {
        upstream.stubFor(get(urlEqualTo("/pokemon/1"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500).withBody("{}")));

        assertThatThrownBy(() -> client.get("/pokemon/1", PokeApiNameRef.class))
                .isInstanceOf(UpstreamTimeoutException.class)
                .hasMessageContaining("/pokemon/1");
    }

    @Test
    void should_raise_upstream_unavailable_when_the_payload_is_malformed() {
        upstream.stubFor(get(urlEqualTo("/pokemon/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\": ")));

        assertThatThrownBy(() -> client.get("/pokemon/1", PokeApiNameRef.class))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    // once the breaker is open the call fails without reaching upstream at all
    @Test
    void should_stop_calling_upstream_after_five_consecutive_failed_calls() {
        upstream.stubFor(get(urlPathEqualTo("/pokemon/1")).willReturn(aResponse().withStatus(500)));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> client.get("/pokemon/1", PokeApiNameRef.class))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
        upstream.resetRequests();

        assertThatThrownBy(() -> client.get("/pokemon/1", PokeApiNameRef.class))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("circuit");

        upstream.verify(0, getRequestedFor(urlPathEqualTo("/pokemon/1")));
    }

    @Test
    void should_close_the_circuit_again_once_a_call_succeeds() {
        upstream.stubFor(get(urlEqualTo("/pokemon/1")).willReturn(aResponse().withStatus(500)));
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> client.get("/pokemon/1", PokeApiNameRef.class))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        upstream.stubFor(get(urlEqualTo("/pokemon/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"bulbasaur\"}")));

        assertThat(client.get("/pokemon/1", PokeApiNameRef.class))
                .map(PokeApiNameRef::name)
                .contains("bulbasaur");
    }
}

package com.elatusdev.pokedex.catalog.infrastructure;

import com.elatusdev.pokedex.catalog.domain.UpstreamTimeoutException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// The only place that speaks HTTP to PokeAPI. Timeouts, retry and the breaker live here so
// the fan-out and the mapper never have to think about them — docs/handbook/concurrency.md.
public class PokeApiClient {

    private static final Logger log = LoggerFactory.getLogger(PokeApiClient.class);

    private final RestClient restClient;
    private final PokeApiProperties properties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    public PokeApiClient(PokeApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build();
    }

    public <T> Optional<T> get(String path, Class<T> type) {
        requireCircuitClosed(path);
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            Outcome<T> outcome = attemptOnce(path, type);
            if (outcome.succeeded()) {
                consecutiveFailures.set(0);
                return outcome.body();
            }
            lastFailure = outcome.failure();
            if (!outcome.retryable()) {
                break;
            }
            backOff(attempt);
        }
        return recordFailure(lastFailure);
    }

    private <T> Outcome<T> attemptOnce(String path, Class<T> type) {
        try {
            return Outcome.ok(Optional.ofNullable(restClient.get().uri(path).retrieve().body(type)));
        } catch (HttpClientErrorException.NotFound absent) {
            // an absent resource is an answer, not a failure — and it resets the breaker
            return Outcome.ok(Optional.empty());
        } catch (HttpClientErrorException.TooManyRequests throttled) {
            return Outcome.transientFailure(unavailable(path, throttled));
        } catch (HttpServerErrorException serverError) {
            return Outcome.transientFailure(unavailable(path, serverError));
        } catch (ResourceAccessException io) {
            return Outcome.transientFailure(translateIo(path, io));
        } catch (HttpClientErrorException clientError) {
            // every other 4xx is our fault and will not become a 200
            return Outcome.permanentFailure(unavailable(path, clientError));
        } catch (RestClientException malformed) {
            return Outcome.permanentFailure(unavailable(path, malformed));
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(PokeApiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    private static RuntimeException translateIo(String path, ResourceAccessException io) {
        return io.getCause() instanceof SocketTimeoutException
                ? new UpstreamTimeoutException("pokeapi timed out for " + path, io)
                : unavailable(path, io);
    }

    private static UpstreamUnavailableException unavailable(String path, Exception cause) {
        return new UpstreamUnavailableException("pokeapi failed for " + path, cause);
    }

    private void requireCircuitClosed(String path) {
        if (consecutiveFailures.get() >= properties.circuitBreakerThreshold()) {
            throw new UpstreamUnavailableException(
                    "pokeapi circuit is open after " + consecutiveFailures.get() + " consecutive failures", null);
        }
        log.debug("pokeapi GET {}", path);
    }

    private <T> Optional<T> recordFailure(RuntimeException failure) {
        consecutiveFailures.incrementAndGet();
        throw failure;
    }

    // The no-Thread.sleep rule is about tests, which must never wait on wall-clock. A retry
    // backoff genuinely has to wait, and on a virtual thread this parks the task and releases
    // the carrier rather than blocking a platform thread.
    private void backOff(int attempt) {
        try {
            Thread.sleep(properties.retryBackoff().multipliedBy(1L << attempt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException("pokeapi retry was interrupted", interrupted);
        }
    }

    private record Outcome<T>(Optional<T> body, RuntimeException failure, boolean retryable) {

        static <T> Outcome<T> ok(Optional<T> body) {
            return new Outcome<>(body, null, false);
        }

        static <T> Outcome<T> transientFailure(RuntimeException failure) {
            return new Outcome<>(null, failure, true);
        }

        static <T> Outcome<T> permanentFailure(RuntimeException failure) {
            return new Outcome<>(null, failure, false);
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}

package com.elatusdev.pokedex.catalog.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pokeapi")
public record PokeApiProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        Duration retryBackoff,
        // in-flight calls; the page-size cap bounds how many a request can queue
        int maxConcurrency,
        Duration cacheTtl,
        int circuitBreakerThreshold) {}

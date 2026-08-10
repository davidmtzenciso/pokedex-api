package com.elatusdev.pokedex.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "pokedex.jwt")
public record JwtProperties(
        Resource keystorePath,
        String keystorePassword,
        String keyAlias,
        String keyId,
        String issuer,
        String audience,
        Duration accessTtl,
        Duration refreshTtl) {}

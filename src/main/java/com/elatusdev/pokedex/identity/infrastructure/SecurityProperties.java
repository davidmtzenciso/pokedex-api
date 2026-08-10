package com.elatusdev.pokedex.identity.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pokedex.security")
public record SecurityProperties(List<String> corsAllowedOrigins, int loginRateLimit, Duration loginRateWindow) {}

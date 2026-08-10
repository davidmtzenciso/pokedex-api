package com.elatusdev.pokedex.domain.port;

import java.time.Duration;
import java.util.Optional;

// reads fail open: a cache miss and a cache outage are the same thing to a caller. The
// session store deliberately does the opposite — see docs/handbook/error-handling.md
public interface CachePort {

    <T> Optional<T> get(String key, Class<T> type);

    <T> void put(String key, T value, Duration ttl);

    void evict(String key);
}

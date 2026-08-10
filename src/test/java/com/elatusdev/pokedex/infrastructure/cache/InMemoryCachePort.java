package com.elatusdev.pokedex.infrastructure.cache;

import com.elatusdev.pokedex.domain.port.CachePort;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// A real map rather than a mock: the read-through tests care what the cache CONTAINS, and
// a mock would need any()-style stubbing to answer a key it was not told about.
public class InMemoryCachePort implements CachePort {

    private final Map<String, Object> entries = new ConcurrentHashMap<>();
    private final Map<String, Duration> ttls = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(entries.get(key)).map(type::cast);
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        entries.put(key, value);
        ttls.put(key, ttl);
    }

    @Override
    public void evict(String key) {
        entries.remove(key);
        ttls.remove(key);
    }

    @Override
    public void evictByPrefix(String prefix) {
        entries.keySet().removeIf(key -> key.startsWith(prefix));
        ttls.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Optional<Duration> ttlOf(String key) {
        return Optional.ofNullable(ttls.get(key));
    }
}

package com.elatusdev.pokedex.catalog.infrastructure.cache;

import com.elatusdev.pokedex.shared.port.CachePort;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Reads FAIL OPEN: an unreachable Redis is reported as a miss, so the caller falls through
// to upstream and the request still succeeds. A cache outage is not an outage.
//
// The session store in WF-AUTH does the exact opposite and fails closed. The two look
// alike and must not be made to share this error handling — docs/handbook/error-handling.md.
public class RedisCacheAdapter implements CachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);

    // values are stored as JSON strings rather than through a Redis serializer, so nothing
    // in the cache depends on a serializer's binary format surviving a library upgrade
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final StringRedisTemplate redis;

    public RedisCacheAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key)).map(json -> deserialise(json, type));
        } catch (DataAccessException unreachable) {
            log.warn("cache read failed for {}, falling through to upstream: {}", key, unreachable.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            redis.opsForValue().set(key, MAPPER.writeValueAsString(value), ttl);
        } catch (DataAccessException | JacksonException notCached) {
            log.warn("cache write failed for {}: {}", key, notCached.getMessage());
        }
    }

    @Override
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException unreachable) {
            log.warn("cache evict failed for {}: {}", key, unreachable.getMessage());
        }
    }

    @Override
    public void evictByPrefix(String prefix) {
        try {
            Set<String> matching = redis.keys(prefix + "*");
            if (!matching.isEmpty()) {
                redis.delete(matching);
            }
        } catch (DataAccessException unreachable) {
            log.warn("cache evict failed for prefix {}: {}", prefix, unreachable.getMessage());
        }
    }

    // a value we cannot read back is a miss, not a failure — the shape may simply predate
    // the current record definition
    private <T> T deserialise(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException unreadable) {
            log.warn("cached value for type {} could not be read back: {}", type.getSimpleName(),
                    unreadable.getMessage());
            return null;
        }
    }
}

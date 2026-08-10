package com.elatusdev.pokedex.identity.infrastructure;

import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.identity.domain.SessionStore;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// FAIL CLOSED. This is the deliberate opposite of the cache adapter, which fails open, and
// the two look almost identical — same dependency, same exception, opposite return value.
// A cache is an optimisation, so losing it costs latency. A session store is a security
// control, so losing it must cost access: during a Redis outage every protected request
// gets 401, never 200 (AC-AUTH-6, risk R14).
@Component
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);

    private static final String PREFIX = "pokedex:session:";

    private final StringRedisTemplate redis;
    private final ClockPort clock;

    public RedisSessionStore(StringRedisTemplate redis, ClockPort clock) {
        this.redis = redis;
        this.clock = clock;
    }

    // deliberately NOT fail-closed-and-quiet: a login that returns a token whose session was
    // never written hands the caller a credential that 401s on its first use. The failure
    // belongs to the login, so it propagates.
    @Override
    public void open(String jti, UserId subject, Instant expiresAt) {
        Duration lifetime = Duration.between(clock.now(), expiresAt);
        if (!lifetime.isPositive()) {
            throw new IllegalArgumentException("session for " + jti + " is already expired");
        }
        redis.opsForValue().set(key(jti), Long.toString(subject.value()), lifetime);
    }

    @Override
    public boolean isLive(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(jti)));
        } catch (DataAccessException e) {
            // DataAccessException rather than RedisConnectionFailureException: a timeout is
            // an outage that has not admitted it yet, and it arrives as a different subtype.
            // Catching the narrow one would fail OPEN on exactly the slow-Redis case.
            log.error("security: session store unreachable, denying the request — {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void close(String jti) {
        redis.delete(key(jti));
    }

    private static String key(String jti) {
        return PREFIX + jti;
    }
}

package com.elatusdev.pokedex.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.identity.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisSessionStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(900);
    private static final UserId SUBJECT = UserId.of(7);
    private static final String JTI = "jti-1";
    private static final String KEY = "pokedex:session:jti-1";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private ClockPort clock;

    private RedisSessionStore store() {
        return new RedisSessionStore(redis, clock);
    }

    @Test
    void should_write_the_session_with_the_remaining_lifetime_of_the_token() {
        when(redis.opsForValue()).thenReturn(values);
        when(clock.now()).thenReturn(NOW);

        store().open(JTI, SUBJECT, EXPIRES);

        // the value is the subject id and never an email: a session store is not a place to
        // put personal data either
        verify(values, times(1)).set(KEY, "7", Duration.ofSeconds(900));
        verify(redis, times(1)).opsForValue();
        verify(clock, times(1)).now();
        verifyNoMoreInteractions(redis, values, clock);
    }

    @Test
    void should_report_a_session_live_while_its_key_exists() {
        when(redis.hasKey(KEY)).thenReturn(Boolean.TRUE);

        assertThat(store().isLive(JTI)).isTrue();

        verify(redis, times(1)).hasKey(KEY);
        verifyNoMoreInteractions(redis);
    }

    // this is what makes logout real: the signature is still perfect, the key is gone
    @Test
    void should_report_a_session_dead_once_its_key_is_gone() {
        when(redis.hasKey(KEY)).thenReturn(Boolean.FALSE);

        assertThat(store().isLive(JTI)).isFalse();
    }

    @Test
    void should_report_a_session_dead_when_redis_answers_with_nothing() {
        when(redis.hasKey(KEY)).thenReturn(null);

        assertThat(store().isLive(JTI)).isFalse();
    }

    // AC-AUTH-6 / risk R14. A session store is a security control, not an optimisation: the
    // cache next to it returns empty here and falls through to upstream, and copying that
    // handler into this class would silently grant access for the length of a Redis outage.
    @Test
    void should_deny_the_request_when_the_session_store_is_unreachable() {
        when(redis.hasKey(KEY)).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(store().isLive(JTI)).isFalse();
    }

    // a timeout is an outage that has not admitted it yet, and it arrives as a different
    // exception type — catching only RedisConnectionFailureException would fail OPEN here
    @Test
    void should_deny_the_request_when_the_session_store_times_out() {
        when(redis.hasKey(KEY)).thenThrow(new QueryTimeoutException("timed out"));

        assertThat(store().isLive(JTI)).isFalse();
    }

    @Test
    void should_delete_the_session_on_logout() {
        when(redis.delete(KEY)).thenReturn(Boolean.TRUE);

        store().close(JTI);

        verify(redis, times(1)).delete(KEY);
        verifyNoMoreInteractions(redis);
    }

    // opening a session must NOT fail closed-and-quiet: a login that returns a token whose
    // session was never written hands the caller a credential that 401s on first use
    @Test
    void should_fail_the_login_when_the_session_cannot_be_written() {
        when(redis.opsForValue()).thenReturn(values);
        when(clock.now()).thenReturn(NOW);
        RedisConnectionFailureException outage = new RedisConnectionFailureException("connection refused");
        doThrow(outage).when(values).set(KEY, "7", Duration.ofSeconds(900));

        assertThatThrownBy(() -> store().open(JTI, SUBJECT, EXPIRES)).isSameAs(outage);
    }

    @Test
    void should_reject_a_session_whose_token_has_already_expired() {
        when(clock.now()).thenReturn(EXPIRES);

        assertThatThrownBy(() -> store().open(JTI, SUBJECT, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already expired");
    }
}

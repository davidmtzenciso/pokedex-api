package com.elatusdev.pokedex.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.domain.port.ClockPort;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginRateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int LIMIT = 3;
    private static final String CALLER = "203.0.113.7";

    @Mock
    private ClockPort clock;

    // a counting stub rather than a mock: the assertion is "how many attempts reached the
    // chain", and verifying that on a mock would need any() matchers, which this project
    // bans for good reason — they pass when the code uses the wrong value
    private final AtomicInteger passedThrough = new AtomicInteger();

    private final FilterChain chain = (request, response) -> passedThrough.incrementAndGet();

    private LoginRateLimitFilter filter() {
        return new LoginRateLimitFilter(clock, new SecurityProperties(List.of("http://localhost"), LIMIT, WINDOW));
    }

    private static MockHttpServletRequest request(String uri, String caller) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(caller);
        return request;
    }

    private MockHttpServletResponse attempt(LoginRateLimitFilter filter, String caller) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/security/login", caller), response, chain);
        return response;
    }

    @Test
    void should_pass_every_attempt_through_while_the_caller_is_within_budget() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();

        for (int attempt = 0; attempt < LIMIT; attempt++) {
            assertThat(attempt(filter, CALLER).getStatus()).isEqualTo(200);
        }

        assertThat(passedThrough).hasValue(LIMIT);
    }

    // OWASP A07: the attempt that would have cost a BCrypt round at cost 12 never reaches it
    @Test
    void should_reject_the_attempt_past_the_budget_without_calling_the_chain() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            attempt(filter, CALLER);
        }

        MockHttpServletResponse rejected = attempt(filter, CALLER);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getContentType()).startsWith("application/problem+json");
        assertThat(rejected.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"").contains("\"status\":429");
        assertThat(passedThrough).hasValue(LIMIT);
    }

    // the window slides rather than resetting on a fixed boundary, so a caller who waits it
    // out is served again without ever getting a free burst at the top of a minute
    @Test
    void should_serve_the_caller_again_once_the_window_has_passed() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            attempt(filter, CALLER);
        }
        assertThat(attempt(filter, CALLER).getStatus()).isEqualTo(429);

        when(clock.now()).thenReturn(NOW.plus(WINDOW).plusSeconds(1));

        assertThat(attempt(filter, CALLER).getStatus()).isEqualTo(200);
    }

    @Test
    void should_still_reject_while_the_oldest_attempt_is_inside_the_window() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            attempt(filter, CALLER);
        }

        when(clock.now()).thenReturn(NOW.plus(WINDOW).minusSeconds(1));

        assertThat(attempt(filter, CALLER).getStatus()).isEqualTo(429);
    }

    // one caller exhausting its budget must not lock anybody else out
    @Test
    void should_budget_each_caller_independently() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            attempt(filter, CALLER);
        }
        assertThat(attempt(filter, CALLER).getStatus()).isEqualTo(429);

        assertThat(attempt(filter, "198.51.100.4").getStatus()).isEqualTo(200);
    }

    @Test
    void should_not_filter_a_path_outside_the_credential_endpoints() {
        LoginRateLimitFilter filter = filter();

        assertThat(filter.shouldNotFilter(request("/api/v1/pokedex/pokemon", CALLER)))
                .isTrue();
        assertThat(filter.shouldNotFilter(request("/api/actuator/health", CALLER)))
                .isTrue();
        assertThat(filter.shouldNotFilter(request("/api/v1/security/login", CALLER)))
                .isFalse();
        assertThat(filter.shouldNotFilter(request("/api/v1/security/register", CALLER)))
                .isFalse();
    }

    // the map is keyed by caller address: without eviction a distributed flood turns the
    // defence into the memory leak that takes the service down instead
    @Test
    void should_evict_idle_callers_once_the_tracked_set_grows_past_its_bound() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();
        for (int caller = 0; caller < 10_050; caller++) {
            attempt(filter, "10.0." + (caller / 250) + "." + (caller % 250));
        }

        // every one of those windows is now older than the cutoff, so the sweep can drain
        // them and the filter still answers correctly afterwards
        when(clock.now()).thenReturn(NOW.plus(WINDOW).plusSeconds(1));

        assertThat(attempt(filter, "10.0.0.0").getStatus()).isEqualTo(200);
        assertThat(filter.trackedCallers()).isLessThan(10_050);
    }

    @Test
    void should_not_reject_a_caller_whose_earlier_attempts_have_all_aged_out() throws Exception {
        when(clock.now()).thenReturn(NOW);
        LoginRateLimitFilter filter = filter();
        attempt(filter, CALLER);

        when(clock.now()).thenReturn(NOW.plus(WINDOW).plusSeconds(30));

        for (int attempt = 0; attempt < LIMIT; attempt++) {
            assertThat(attempt(filter, CALLER).getStatus()).isEqualTo(200);
        }
        assertThat(passedThrough).hasValue(1 + LIMIT);
    }
}

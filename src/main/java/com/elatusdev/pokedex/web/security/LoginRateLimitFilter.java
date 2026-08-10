package com.elatusdev.pokedex.web.security;

import com.elatusdev.pokedex.domain.port.ClockPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// First in the chain: an unauthenticated flood is rejected before it costs a signature
// verification, let alone a BCrypt round at cost 12 — which is the expensive thing an
// attacker gets to spend our CPU on for free (OWASP A07).
//
// Scoped to the credential endpoints rather than the whole API. A global in-memory limiter
// would be the wrong shape anyway: it does not survive a restart and does not coordinate
// across instances, so this is a brute-force speed bump and is documented as one rather
// than presented as a rate-limiting tier.
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String GUARDED_PREFIX = "/v1/security/";

    // the map is keyed by caller address, so without a bound a distributed flood turns the
    // defence into the memory leak that takes the service down instead
    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final ClockPort clock;
    private final SecurityProperties properties;

    public LoginRateLimitFilter(ClockPort clock, SecurityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains(GUARDED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (withinBudget(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        ProblemDetails.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests",
                "Too many attempts from this address; retry shortly",
                "RATE_LIMITED");
    }

    private boolean withinBudget(String caller) {
        Instant now = clock.now();
        Instant cutoff = now.minus(properties.loginRateWindow());
        evictIdleCallers(cutoff);
        Deque<Instant> window = attempts.computeIfAbsent(caller, key -> new ConcurrentLinkedDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= properties.loginRateLimit()) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    // package-private: eviction is only observable through the size of the tracked set, and
    // a bound that is never asserted is a bound nobody notices breaking
    int trackedCallers() {
        return attempts.size();
    }

    private void evictIdleCallers(Instant cutoff) {
        if (attempts.size() <= MAX_TRACKED_CALLERS) {
            return;
        }
        attempts.values().removeIf(window -> {
            synchronized (window) {
                return window.isEmpty() || window.peekLast().isBefore(cutoff);
            }
        });
    }
}

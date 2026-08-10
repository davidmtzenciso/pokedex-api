package com.elatusdev.pokedex.identity.infrastructure;

import com.elatusdev.pokedex.identity.domain.Role;
import com.elatusdev.pokedex.identity.domain.TokenType;
import com.elatusdev.pokedex.identity.domain.VerifiedToken;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.identity.domain.TokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Cryptography before I/O: a forged token is rejected on its signature here, and never
// costs the session store a round trip. Only an ACCESS token authenticates a request — a
// refresh token is a credential for the rotation endpoint alone.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final TokenIssuer tokenIssuer;
    private final ClockPort clock;

    public JwtAuthenticationFilter(TokenIssuer tokenIssuer, ClockPort clock) {
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> presented = bearerToken(request);
        if (presented.isEmpty()) {
            // no token is not a failure here: the route may well be public, and it is the
            // authorization rules that decide. The entry point defaults to UNAUTHENTICATED.
            chain.doFilter(request, response);
            return;
        }
        Optional<VerifiedToken> verified = tokenIssuer
                .verify(presented.get(), clock.now())
                .filter(token -> token.type() == TokenType.ACCESS);
        if (verified.isEmpty()) {
            request.setAttribute(ProblemDetailEntryPoint.REASON, AuthenticationFailure.INVALID_TOKEN);
            chain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(authentication(verified.get()));
        chain.doFilter(request, response);
    }

    private static UsernamePasswordAuthenticationToken authentication(VerifiedToken token) {
        List<SimpleGrantedAuthority> authorities = token.roles().stream()
                .map(Role::name)
                .sorted()
                .map(name -> new SimpleGrantedAuthority("ROLE_" + name))
                .map(SimpleGrantedAuthority.class::cast)
                .toList();
        return new UsernamePasswordAuthenticationToken(token, null, authorities);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER)
                ? Optional.of(header.substring(BEARER.length()))
                : Optional.empty();
    }
}

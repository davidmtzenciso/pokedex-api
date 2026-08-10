package com.elatusdev.pokedex.identity.infrastructure;

import com.elatusdev.pokedex.identity.domain.VerifiedToken;
import com.elatusdev.pokedex.identity.domain.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// This is what makes logout real. A token with a perfect ES256 signature and a future exp,
// whose jti is no longer in the store, is cleared here and the request goes on to be
// rejected by the authorization rules.
//
// The store fails CLOSED, so an outage clears the context too: no session could be
// confirmed, therefore no request is authenticated (AC-AUTH-6).
@Component
public class SessionCheckFilter extends OncePerRequestFilter {

    private final SessionStore sessions;

    public SessionCheckFilter(SessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof VerifiedToken token
                && !sessions.isLive(token.jti())) {
            SecurityContextHolder.clearContext();
            request.setAttribute(ProblemDetailEntryPoint.REASON, AuthenticationFailure.TOKEN_REVOKED);
        }
        chain.doFilter(request, response);
    }
}

package com.elatusdev.pokedex.identity.web.security;

import com.elatusdev.pokedex.identity.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.identity.domain.model.VerifiedToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// The generated SecurityApi declares logout() and getCurrentUser() with no parameters, so
// the principal cannot arrive as an argument. It is read from the context instead, which is
// where JwtAuthenticationFilter put it.
public final class Principals {

    private Principals() {}

    public static VerifiedToken current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof VerifiedToken token)) {
            throw new InvalidTokenException();
        }
        return token;
    }
}

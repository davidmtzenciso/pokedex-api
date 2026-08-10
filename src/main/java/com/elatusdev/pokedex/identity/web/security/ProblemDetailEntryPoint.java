package com.elatusdev.pokedex.identity.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.elatusdev.pokedex.shared.web.error.ProblemDetails;

// The 401 side of the chain. The code carried here is the one the request earned:
// UNAUTHENTICATED for a missing token, INVALID_TOKEN for one that failed verification, and
// TOKEN_REVOKED for one whose session is gone (§9.5).
@Component
public class ProblemDetailEntryPoint implements AuthenticationEntryPoint {

    static final String REASON = ProblemDetailEntryPoint.class.getName() + ".reason";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException {
        AuthenticationFailure reason = reasonOf(request);
        ProblemDetails.write(
                request, response, HttpStatus.UNAUTHORIZED, "Unauthorized", reason.detail(), reason.code());
    }

    private static AuthenticationFailure reasonOf(HttpServletRequest request) {
        return request.getAttribute(REASON) instanceof AuthenticationFailure failure
                ? failure
                : AuthenticationFailure.UNAUTHENTICATED;
    }
}

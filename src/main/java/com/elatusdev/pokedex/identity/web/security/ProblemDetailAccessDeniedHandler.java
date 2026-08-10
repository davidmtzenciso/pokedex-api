package com.elatusdev.pokedex.identity.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// A 403 means the principal is known and lacks the role. It never means a bad signature —
// that is a 401, and chasing key configuration after a 403 wastes an afternoon.
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
            throws IOException {
        ProblemDetails.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "The authenticated principal does not hold the required role",
                "FORBIDDEN");
    }
}

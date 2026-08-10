package com.elatusdev.pokedex.identity.web.error;

import com.elatusdev.pokedex.identity.domain.exception.InvalidCredentialsException;
import com.elatusdev.pokedex.identity.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.identity.domain.exception.TokenReuseDetectedException;
import com.elatusdev.pokedex.identity.domain.exception.UserAlreadyExistsException;
import com.elatusdev.pokedex.shared.web.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.elatusdev.pokedex.catalog.web.error.CatalogExceptionHandler;

// The WF-AUTH rows of the §9.5 matrix. One advice per context, mirroring
// CatalogExceptionHandler: an advice that maps another context's exceptions would make
// shared depend on that context, which BC3 forbids for good reason.
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    ProblemDetail handleUnauthenticated(RuntimeException failure, HttpServletRequest request) {
        // one shape for both: a login failure and a bad token must not be distinguishable
        // by anything a caller can read
        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED, "Unauthorized", failure.getMessage(), "INVALID_CREDENTIALS", uri(request));
    }

    @ExceptionHandler(TokenReuseDetectedException.class)
    ProblemDetail handleReuse(TokenReuseDetectedException failure, HttpServletRequest request) {
        // the family is already revoked by the time this runs — the use case does it
        // synchronously rather than leaving it to the handler
        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                failure.getMessage(),
                "TOKEN_REUSE_DETECTED",
                uri(request));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ProblemDetail handleDuplicate(UserAlreadyExistsException failure, HttpServletRequest request) {
        return ProblemDetails.of(
                HttpStatus.CONFLICT, "Conflict", failure.getMessage(), "USER_ALREADY_EXISTS", uri(request));
    }

    private static String uri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}

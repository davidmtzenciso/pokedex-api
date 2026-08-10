package com.elatusdev.pokedex.web.controller;

import com.elatusdev.pokedex.domain.exception.InvalidCredentialsException;
import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.domain.exception.TokenReuseDetectedException;
import com.elatusdev.pokedex.domain.exception.UserAlreadyExistsException;
import com.elatusdev.pokedex.web.security.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// The one place an exception becomes a status code. This advice currently covers the WF-AUTH
// rows of the §9.5 matrix; the catalogue and local-CRUD rows land with the stories that
// raise them (WU-US04-B), on this same class.
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    // IA9 — parameter validation surfaces as TWO exception types. Mapping one of them
    // returns 500 for half of all validation failures, and the half is not obvious.
    @ExceptionHandler({
        InvalidPokemonDataException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class,
        MethodArgumentNotValidException.class
    })
    ProblemDetail handleValidation(Exception failure, HttpServletRequest request) {
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "Bad Request", failure.getMessage(), "VALIDATION_ERROR", uri(request));
    }

    private static String uri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}

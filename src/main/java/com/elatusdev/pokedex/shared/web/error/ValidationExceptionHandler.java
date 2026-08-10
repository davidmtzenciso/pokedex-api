package com.elatusdev.pokedex.shared.web.error;

import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Request BODY validation only, and deliberately context-free: both types it claims belong
// to shared or to Spring, so this advice depends on no context and BC3 holds.
//
// The two PARAMETER validation types of IA9 — ConstraintViolationException and
// HandlerMethodValidationException — are NOT claimed here. CatalogExceptionHandler maps them
// to INVALID_PAGINATION, which §9.5 requires and which is strictly more specific than
// VALIDATION_ERROR. Two advices claiming one exception type is resolved by advice ordering
// rather than by specificity, so the broader one would silently win and the better code
// would disappear.
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler({InvalidPokemonDataException.class, MethodArgumentNotValidException.class})
    ProblemDetail handleValidation(Exception failure, HttpServletRequest request) {
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "Bad Request", failure.getMessage(), "VALIDATION_ERROR", uri(request));
    }

    private static String uri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}

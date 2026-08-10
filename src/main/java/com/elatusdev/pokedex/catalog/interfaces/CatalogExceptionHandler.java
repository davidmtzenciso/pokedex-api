package com.elatusdev.pokedex.catalog.interfaces;

import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import com.elatusdev.pokedex.catalog.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.catalog.domain.UpstreamTimeoutException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.contract.dto.FieldErrorDTO;
import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// Scoped to the catalogue so three parallel streams are not editing one advice class.
// WU-US04-B adds the local-CRUD rows to this same advice — one per context.
@RestControllerAdvice
public class CatalogExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://pokedex.elatus-dev.com/problems/";

    // IA9 — parameter validation on a generated *Api interface surfaces as TWO exception
    // types. Mapping only one returns 500 for half of all validation failures, and the half
    // that breaks depends on whether the @Validated proxy or MVC method validation fires.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetailDTO> onMethodValidation(HandlerMethodValidationException rejected) {
        // IA8 — renamed from getAllValidationResults() in Framework 7
        List<FieldErrorDTO> errors = rejected.getParameterValidationResults().stream()
                .map(result -> new FieldErrorDTO(
                        result.getMethodParameter().getParameterName(),
                        result.getResolvableErrors().getFirst().getDefaultMessage()))
                .toList();
        return respond(invalidPagination().errors(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetailDTO> onConstraintViolation(ConstraintViolationException rejected) {
        List<FieldErrorDTO> errors = rejected.getConstraintViolations().stream()
                .map(violation ->
                        new FieldErrorDTO(String.valueOf(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return respond(invalidPagination().errors(errors));
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ProblemDetailDTO> onInvalidPagination(InvalidPaginationException rejected) {
        return respond(problemBody(
                HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "invalid-pagination", rejected.getMessage()));
    }

    @ExceptionHandler(PokemonNotFoundUpstreamException.class)
    public ResponseEntity<ProblemDetailDTO> onNotFoundUpstream(PokemonNotFoundUpstreamException absent) {
        return respond(problemBody(
                HttpStatus.NOT_FOUND, "POKEMON_NOT_FOUND_UPSTREAM", "pokemon-not-found", absent.getMessage()));
    }

    // 502, never 500: an upstream outage is not our failure, and the status is how a caller
    // tells "retry later" from "your request was wrong"
    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ProblemDetailDTO> onUpstreamUnavailable(UpstreamUnavailableException outage) {
        log.warn("upstream unavailable: {}", outage.getMessage());
        return respond(problemBody(
                HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "upstream-unavailable", outage.getMessage()));
    }

    @ExceptionHandler(UpstreamTimeoutException.class)
    public ResponseEntity<ProblemDetailDTO> onUpstreamTimeout(UpstreamTimeoutException timeout) {
        log.warn("upstream timed out: {}", timeout.getMessage());
        return respond(problemBody(
                HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "upstream-timeout", timeout.getMessage()));
    }

    private static ProblemDetailDTO invalidPagination() {
        return problemBody(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGINATION",
                "invalid-pagination",
                "page must be >= 0 and size must be between 1 and 100");
    }

    private static ProblemDetailDTO problemBody(HttpStatus status, String code, String slug, String detail) {
        return new ProblemDetailDTO(URI.create(PROBLEM_BASE + slug), status.getReasonPhrase(), status.value(), code)
                .detail(detail)
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static ResponseEntity<ProblemDetailDTO> respond(ProblemDetailDTO body) {
        return ResponseEntity.status(body.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}

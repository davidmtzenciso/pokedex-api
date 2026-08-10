package com.elatusdev.pokedex.catalog.interfaces;

import com.elatusdev.pokedex.catalog.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.catalog.domain.UpstreamTimeoutException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Scoped to the catalogue: one advice per context, each claiming only its own exception
// types. The cross-cutting validation and pagination rows moved to
// shared/ValidationExceptionHandler — they are not catalogue concerns, and leaving them
// here made every parameter failure anywhere in the API answer INVALID_PAGINATION.
@RestControllerAdvice
public class CatalogExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://pokedex.elatus-dev.com/problems/";




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

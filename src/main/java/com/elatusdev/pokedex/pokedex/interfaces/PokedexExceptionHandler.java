package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.elatusdev.pokedex.pokedex.domain.BatchRangeTooLargeException;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.UpstreamReplicationFailedException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// The pokedex context's advice, the fourth of the per-context set ADR-0013 prescribes. A
// single global handler would have to import every context's exceptions and make shared
// depend on all of them, which is the BC3 violation the split exists to prevent.
//
// It claims no exception another advice already claims. InvalidPokemonDataException in
// particular is deliberately absent: shared's ValidationExceptionHandler owns it, and two
// advices claiming one type is resolved by ordering rather than specificity — the broader
// one silently wins and the narrower mapping never runs.
@RestControllerAdvice
public class PokedexExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PokedexExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://pokedex.elatus-dev.com/problems/";

    @ExceptionHandler(PokemonNotFoundException.class)
    public ResponseEntity<ProblemDetailDTO> onNotFound(PokemonNotFoundException absent) {
        return respond(problemBody(HttpStatus.NOT_FOUND, "POKEMON_NOT_FOUND", "pokemon-not-found", absent));
    }

    // 409 — re-sync was asked for on a record that is neither STALE nor FAILED. The request
    // is not malformed; it is asking for a transition the six-state machine does not have.
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetailDTO> onIllegalTransition(IllegalStateTransitionException rejected) {
        return respond(problemBody(
                HttpStatus.CONFLICT, "ILLEGAL_STATE_TRANSITION", "illegal-state-transition", rejected));
    }

    @ExceptionHandler(DuplicatePokemonException.class)
    public ResponseEntity<ProblemDetailDTO> onDuplicate(DuplicatePokemonException duplicate) {
        return respond(problemBody(HttpStatus.CONFLICT, "DUPLICATE_POKEMON", "duplicate-pokemon", duplicate));
    }

    // 412 rather than 409: the request was well formed and the caller simply held a version
    // that has since moved. That distinction is what tells them to reload rather than to
    // change what they sent — and the alternative is a silent lost update.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetailDTO> onStaleVersion(OptimisticLockingFailureException conflict) {
        return respond(problemBody(
                HttpStatus.PRECONDITION_FAILED, "STALE_VERSION", "stale-version", conflict));
    }

    // 400, and rejected rather than truncated — the same reasoning as the page-size cap.
    // Syncing the first 200 of 1000 leaves the caller believing all 1000 are replicated.
    @ExceptionHandler(BatchRangeTooLargeException.class)
    public ResponseEntity<ProblemDetailDTO> onBatchTooLarge(BatchRangeTooLargeException rejected) {
        return respond(problemBody(
                HttpStatus.BAD_REQUEST, "BATCH_RANGE_TOO_LARGE", "batch-range-too-large", rejected));
    }

    // 502, never 500: PokeAPI being unreachable is not this service failing, and the status
    // is how a caller tells "retry later" from "your request was wrong". The contract lists
    // 502 and not 504 for sync, so a timeout lands here too.
    @ExceptionHandler(UpstreamReplicationFailedException.class)
    public ResponseEntity<ProblemDetailDTO> onUpstreamFailed(UpstreamReplicationFailedException outage) {
        log.warn("upstream replication failed for {}", outage.idOrName());
        return respond(problemBody(
                HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "upstream-unavailable", outage));
    }

    private static ProblemDetailDTO problemBody(HttpStatus status, String code, String slug, Exception cause) {
        return new ProblemDetailDTO(URI.create(PROBLEM_BASE + slug), status.getReasonPhrase(), status.value(), code)
                .detail(cause.getMessage())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static ResponseEntity<ProblemDetailDTO> respond(ProblemDetailDTO body) {
        return ResponseEntity.status(body.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}

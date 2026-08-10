package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.elatusdev.pokedex.pokedex.domain.BatchRangeTooLargeException;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.UpstreamReplicationFailedException;
import com.elatusdev.pokedex.shared.interfaces.ProblemResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// The local-CRUD rows of WF-US04 §9.5. One advice per context, and every type here is
// claimed by this advice alone — the cross-cutting validation rows belong to shared.
//
// The story names 404 and 400 and then asks for "further defensive logic as required".
// These are that: 409 and 412 for two conditions that are easy to conflate and mean
// entirely different things to a client.
@RestControllerAdvice
public class PokedexExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PokedexExceptionHandler.class);

    // AC-US04-2 — the 404 the story names explicitly
    @ExceptionHandler(PokemonNotFoundException.class)
    public ResponseEntity<ProblemDetailDTO> onNotFound(PokemonNotFoundException absent) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.NOT_FOUND, "POKEMON_NOT_FOUND", "pokemon-not-found", absent.getMessage()));
    }

    // 409, not 412: "that already exists". The caller has to choose a different id, and no
    // amount of re-reading will help them.
    @ExceptionHandler(DuplicatePokemonException.class)
    public ResponseEntity<ProblemDetailDTO> onDuplicate(DuplicatePokemonException duplicate) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.CONFLICT, "DUPLICATE_POKEMON", "duplicate-pokemon", duplicate.getMessage()));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetailDTO> onIllegalTransition(IllegalStateTransitionException illegal) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.CONFLICT,
                "ILLEGAL_STATE_TRANSITION",
                "illegal-state-transition",
                illegal.getMessage()));
    }

    // 412, not 409: "someone changed it since you read it". The caller re-reads, re-applies
    // and retries — a different response from the one a duplicate calls for, which is the
    // whole reason these are two codes and not one (AC-US04-4).
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetailDTO> onStaleVersion(OptimisticLockingFailureException stale) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.PRECONDITION_FAILED, "STALE_VERSION", "stale-version", stale.getMessage()));
    }

    // 400 rather than a truncated batch: a caller who asked for 500 ids and silently got 200
    // believes it replicated the rest (WU-US03-B B3)
    @ExceptionHandler(BatchRangeTooLargeException.class)
    public ResponseEntity<ProblemDetailDTO> onBatchTooLarge(BatchRangeTooLargeException rejected) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.BAD_REQUEST, "BATCH_RANGE_TOO_LARGE", "batch-range-too-large", rejected.getMessage()));
    }

    // 502, never 500: PokeAPI being unreachable is not this service failing, and the status
    // is how a caller tells "retry later" from "your request was wrong"
    @ExceptionHandler(UpstreamReplicationFailedException.class)
    public ResponseEntity<ProblemDetailDTO> onUpstreamFailed(UpstreamReplicationFailedException outage) {
        log.warn("upstream replication failed: {}", outage.getMessage());
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "upstream-unavailable", outage.getMessage()));
    }
}

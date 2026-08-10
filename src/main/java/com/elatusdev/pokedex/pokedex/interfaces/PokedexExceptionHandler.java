package com.elatusdev.pokedex.pokedex.interfaces;

import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.shared.interfaces.ProblemResponses;
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
}

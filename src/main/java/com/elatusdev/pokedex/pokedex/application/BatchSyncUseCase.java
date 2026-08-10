package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.BatchRangeTooLargeException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.UpstreamReplicationFailedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Partial success is the expected outcome against a public API, so it is modelled rather
// than treated as an error: this returns a summary and never throws for one bad id.
//
// Not @Transactional, and emphatically so — each id is its own upstream call and its own
// write, and one failure at id 137 must not roll back the 136 that worked.
@Service
public class BatchSyncUseCase {

    // IA10 — PokeAPI is fair-use rate-limited, and a page of one Pokemon already costs
    // several upstream calls. 200 ids is the cap the workflow sets.
    public static final int MAX_BATCH = 200;

    // in-flight upstream calls across the whole batch. The cap bounds how many ids a request
    // may ask for; this bounds how many are being fetched at once. Both axes are needed.
    private static final int MAX_CONCURRENCY = 16;

    private static final Logger log = LoggerFactory.getLogger(BatchSyncUseCase.class);

    private final SyncPokemonUseCase sync;

    public BatchSyncUseCase(SyncPokemonUseCase sync) {
        this.sync = sync;
    }

    public BatchSyncSummary sync(int from, int to) {
        requireSaneRange(from, to);
        List<Outcome> outcomes = fanOut(from, to);
        List<Integer> failedIds = outcomes.stream()
                .filter(outcome -> outcome.kind() == Kind.FAILED)
                .map(Outcome::id)
                .sorted()
                .toList();
        // one line for the whole batch, not one per id — a 200-id fan-out would otherwise
        // bury everything else in the log
        log.info("batch sync {}..{}: {} succeeded, {} failed, {} skipped", from, to, count(outcomes, Kind.SUCCEEDED),
                failedIds.size(), count(outcomes, Kind.SKIPPED));
        return new BatchSyncSummary(
                count(outcomes, Kind.SUCCEEDED), failedIds.size(), count(outcomes, Kind.SKIPPED), failedIds);
    }

    private List<Outcome> fanOut(int from, int to) {
        Semaphore permits = new Semaphore(MAX_CONCURRENCY);
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Outcome>> pending = IntStream.rangeClosed(from, to)
                    .mapToObj(id -> CompletableFuture.supplyAsync(() -> syncOne(id, permits), workers))
                    .toList();
            return pending.stream().map(CompletableFuture::join).toList();
        }
    }

    private Outcome syncOne(int id, Semaphore permits) {
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome(id, Kind.FAILED);
        }
        try {
            return new Outcome(id, classify(id));
        } finally {
            permits.release();
        }
    }

    // Three named failure modes, never a bare RuntimeException catch. That is possible only
    // because the upstream adapter translates catalog's exceptions into a pokedex one on the
    // way across — without it this would have to catch everything and guess.
    private Kind classify(int id) {
        try {
            sync.sync(String.valueOf(id));
            return Kind.SUCCEEDED;
        } catch (IllegalStateTransitionException upToDate) {
            // not a failure: the record is current, and a re-run would do nothing for it
            return Kind.SKIPPED;
        } catch (PokemonNotFoundException | UpstreamReplicationFailedException failure) {
            return Kind.FAILED;
        }
    }

    private static int count(List<Outcome> outcomes, Kind kind) {
        return (int) outcomes.stream().filter(outcome -> outcome.kind() == kind).count();
    }

    private static void requireSaneRange(int from, int to) {
        int requested = to - from + 1;
        if (requested < 1 || requested > MAX_BATCH) {
            throw new BatchRangeTooLargeException(requested, MAX_BATCH);
        }
    }

    private enum Kind {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    private record Outcome(int id, Kind kind) {
    }
}

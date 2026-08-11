package com.elatusdev.pokedex.pokedex.infrastructure;

import com.elatusdev.pokedex.pokedex.application.BatchSyncSummary;
import com.elatusdev.pokedex.pokedex.application.BatchSyncUseCase;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonFilter;
import com.elatusdev.pokedex.pokedex.domain.LocalPokemonQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

// Replicates the first generation at first boot so a cold `docker compose up` has something
// to show, rather than an empty catalogue that makes every US03 and US04 demo start with
// manual setup.
//
// Through the ordinary sync path, deliberately. A committed SQL dump of 151 Pokemon would
// be a second copy of upstream's data that goes stale the moment upstream changes and that
// no test in this repository can verify — and it would demonstrate none of the replication
// code the exercise is about. This way the seed is the feature, exercised.
//
// The cost is real and is the reason this is opt-in: 151 ids is roughly 450 upstream calls
// against a fair-use API (IA10). BatchSyncUseCase bounds the fan-out at 16 in flight.
@Component
@ConditionalOnProperty(name = "pokedex.seed.upstream-on-startup", havingValue = "true")
public class UpstreamSeedRunner {

    // the original 151. Kanto is what quickstart.md promises and what the demo walks through.
    private static final int FIRST = 1;
    private static final int LAST = 151;

    private static final Logger log = LoggerFactory.getLogger(UpstreamSeedRunner.class);

    private final BatchSyncUseCase batchSync;
    private final LocalPokemonQuery query;

    UpstreamSeedRunner(BatchSyncUseCase batchSync, LocalPokemonQuery query) {
        this.batchSync = batchSync;
        this.query = query;
    }

    // ApplicationReadyEvent, on its own thread: the container is already accepting traffic
    // and passing its readiness probe, so a slow upstream delays the seed and nothing else.
    // Blocking here would make a rate-limited third party decide when the API starts serving.
    @EventListener(ApplicationReadyEvent.class)
    public void seedFromUpstream() {
        Thread.ofVirtual().name("upstream-seed").start(this::replicateFirstGeneration);
    }

    private void replicateFirstGeneration() {
        try {
            long stored = query.count(LocalPokemonFilter.none());
            if (stored > 0) {
                // idempotent: a restart against an existing volume must not re-fetch 151
                // records that are already here
                log.info("upstream seed skipped: {} records already stored", stored);
                return;
            }
            log.info("upstream seed starting: replicating {}..{} from PokeAPI", FIRST, LAST);
            BatchSyncSummary summary = batchSync.sync(FIRST, LAST);
            log.info(
                    "upstream seed finished: {} succeeded, {} failed, {} skipped",
                    summary.succeeded(),
                    summary.failed(),
                    summary.skipped());
        } catch (DataAccessException unavailable) {
            // the seed is a convenience, not a precondition. An empty catalogue is a working
            // API with nothing in it; a failed startup is neither.
            log.warn("upstream seed abandoned: {}", unavailable.getClass().getSimpleName());
        }
    }
}

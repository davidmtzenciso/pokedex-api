package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.PokemonMergePolicy;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.pokedex.domain.UpstreamCatalog;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

// Deliberately NOT @Transactional: the upstream fetch is remote I/O, and holding a database
// transaction open across it exhausts the pool under a slow upstream. The repository adapter
// is transactional per call, which is the right granularity for a single-aggregate write.
@Service
public class SyncPokemonUseCase {

    // A replica is stale once upstream has had a day to change under it. A constant rather
    // than configuration because nothing in WF-000 §8 declares a replica TTL — POKEAPI_CACHE_TTL
    // is the cache's, and conflating the two would make one knob move two unrelated things.
    static final Duration STALENESS_TTL = Duration.ofHours(24);

    private final UpstreamCatalog upstream;
    private final PokemonRepository repository;
    private final PokemonMergePolicy mergePolicy;
    private final ClockPort clock;

    public SyncPokemonUseCase(
            UpstreamCatalog upstream,
            PokemonRepository repository,
            PokemonMergePolicy mergePolicy,
            ClockPort clock) {
        this.upstream = upstream;
        this.repository = repository;
        this.mergePolicy = mergePolicy;
        this.clock = clock;
    }

    public SyncResult sync(String idOrName) {
        return findLocal(idOrName).map(existing -> reSync(existing, idOrName)).orElseGet(() -> replicate(idOrName));
    }

    // The guard runs before the fetch, which is the point of doing it here rather than
    // letting the aggregate reject the transition afterwards: a request that cannot succeed
    // costs nothing upstream, and upstream is the rate-limited resource (IA10).
    private SyncResult reSync(Pokemon existing, String idOrName) {
        markStaleIfExpired(existing);
        requireResyncable(existing);
        UpstreamPokemon fresh = fetchOrThrow(idOrName);
        prepareForMerge(existing);
        mergePolicy.merge(existing, fresh.replicated(), clock.now());
        return new SyncResult(repository.save(existing), false);
    }

    private SyncResult replicate(String idOrName) {
        UpstreamPokemon fresh = fetchOrThrow(idOrName);
        Pokemon created = Pokemon.pending(fresh.pokeApiId(), fresh.replicated());
        created.transitionTo(ReplicationState.SYNCED, clock.now());
        return new SyncResult(repository.save(created), true);
    }

    private Optional<Pokemon> findLocal(String idOrName) {
        return isNumeric(idOrName)
                ? repository.findByPokeApiId(PokeApiId.of(Integer.parseInt(idOrName)))
                : repository.findByName(new PokemonName(idOrName));
    }

    private UpstreamPokemon fetchOrThrow(String idOrName) {
        Optional<UpstreamPokemon> fetched = isNumeric(idOrName)
                ? upstream.fetchById(PokeApiId.of(Integer.parseInt(idOrName)))
                : upstream.fetchByName(new PokemonName(idOrName));
        return fetched.orElseThrow(() -> new PokemonNotFoundException(idOrName));
    }

    // The derived value isStale of WF-000 §4.7, applied. Without it a healthy record is
    // never re-syncable and the contract's 200 response could not occur.
    private void markStaleIfExpired(Pokemon existing) {
        Instant now = clock.now();
        boolean expired = existing.syncedAt()
                .map(at -> !at.plus(STALENESS_TTL).isAfter(now))
                .orElse(false);
        if (expired && existing.replicationState() != ReplicationState.STALE) {
            existing.transitionTo(ReplicationState.STALE, now);
        }
    }

    private static void requireResyncable(Pokemon existing) {
        ReplicationState state = existing.replicationState();
        if (state != ReplicationState.STALE && state != ReplicationState.FAILED) {
            throw new IllegalStateTransitionException(state, ReplicationState.STALE);
        }
    }

    // FAILED → PENDING is the only edge out of FAILED, and PENDING → CUSTOMIZED is not
    // legal, so a failed record carrying curator data has to reach SYNCED before the merge
    // can take it to CUSTOMIZED. A failed record carrying nothing stops at PENDING, because
    // the merge itself supplies PENDING → SYNCED.
    private void prepareForMerge(Pokemon existing) {
        if (existing.replicationState() != ReplicationState.FAILED) {
            return;
        }
        Instant now = clock.now();
        existing.transitionTo(ReplicationState.PENDING, now);
        if (!existing.proprietary().isEmpty()) {
            existing.transitionTo(ReplicationState.SYNCED, now);
        }
    }

    private static boolean isNumeric(String idOrName) {
        return !idOrName.isEmpty() && idOrName.chars().allMatch(Character::isDigit);
    }
}

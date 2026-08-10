package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.shared.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.shared.domain.UpstreamTimeoutException;
import com.elatusdev.pokedex.shared.domain.UpstreamUnavailableException;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemonSource;
import com.elatusdev.pokedex.pokedex.domain.PokemonId;
import com.elatusdev.pokedex.pokedex.domain.PokemonMergePolicy;
import com.elatusdev.pokedex.pokedex.domain.PokemonNotFoundException;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// The only path on which upstream data reaches a stored record. The field merge is delegated
// to PokemonMergePolicy rather than written inline, because the policy is pure precisely so
// F7 can be proven as a property.
//
// Not @Transactional: the catalogue call is remote I/O and holding a connection across it is
// what exhausts the pool under a slow upstream.
@Service
public class ReSyncPokemonUseCase {

    private static final Set<ReplicationState> RE_SYNCABLE =
            EnumSet.of(ReplicationState.STALE, ReplicationState.FAILED);

    private static final Logger log = LoggerFactory.getLogger(ReSyncPokemonUseCase.class);

    private final UpstreamPokemonSource upstreamSource;
    private final PokemonRepository repository;
    private final PokemonMergePolicy mergePolicy;
    private final ClockPort clock;

    public ReSyncPokemonUseCase(
            UpstreamPokemonSource upstreamSource,
            PokemonRepository repository,
            PokemonMergePolicy mergePolicy,
            ClockPort clock) {
        this.upstreamSource = upstreamSource;
        this.repository = repository;
        this.mergePolicy = mergePolicy;
        this.clock = clock;
    }

    public Pokemon reSync(PokemonId id) {
        Pokemon existing = repository.findById(id).orElseThrow(() -> new PokemonNotFoundException(id));
        requireReSyncable(existing);
        // always present: F6 makes DRAFT exactly the set with no pokeApiId, and DRAFT is not
        // re-syncable, so the guard above has already excluded the only state that could be empty
        PokeApiId pokeApiId = existing.pokeApiId().orElseThrow();
        UpstreamPokemon upstream = fetchOrMarkFailed(existing, pokeApiId);
        return repository.save(applyUpstream(existing, upstream, clock.now()));
    }

    // before the network call, deliberately: an upstream request costs a rate-limited call
    // (IA10), and a record in the wrong state will not be allowed to use it either way
    private static void requireReSyncable(Pokemon existing) {
        if (!RE_SYNCABLE.contains(existing.replicationState())) {
            throw new IllegalStateTransitionException(existing.replicationState(), ReplicationState.SYNCED);
        }
    }

    private UpstreamPokemon fetchOrMarkFailed(Pokemon existing, PokeApiId pokeApiId) {
        try {
            return upstreamSource.fetchById(pokeApiId)
                    .orElseThrow(() -> new PokemonNotFoundUpstreamException(String.valueOf(pokeApiId.value())));
        } catch (UpstreamUnavailableException | UpstreamTimeoutException outage) {
            markFailed(existing, outage);
            throw outage;
        }
    }

    // FAILED is already FAILED; STALE -> FAILED is the diagram's edge for a re-sync that
    // exhausted its retries, and it leaves a record retrySync can pick up later
    private void markFailed(Pokemon existing, RuntimeException outage) {
        if (existing.replicationState() == ReplicationState.STALE) {
            existing.transitionTo(ReplicationState.FAILED, clock.now());
            repository.save(existing);
        }
        log.warn("re-sync failed for {}: {}", existing.id().orElse(null), outage.getMessage());
    }

    // a retry re-enters through PENDING: FAILED's only legal successor is PENDING, and
    // PENDING's is SYNCED. replaceReplicated lands on SYNCED and re-customises from there,
    // so no edge outside the diagram is ever taken.
    private Pokemon applyUpstream(Pokemon existing, UpstreamPokemon upstream, Instant now) {
        if (existing.replicationState() == ReplicationState.FAILED) {
            existing.transitionTo(ReplicationState.PENDING, now);
        }
        PokemonMergePolicy.MergedFields merged = mergePolicy.merge(existing, upstream.replicated());
        existing.replaceReplicated(merged.replicated(), now);
        return existing;
    }
}

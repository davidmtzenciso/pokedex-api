package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.shared.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemon;
import com.elatusdev.pokedex.pokedex.domain.UpstreamPokemonSource;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

// First replication of a Pokemon this service has never stored. Refreshing one it already
// has is ReSyncPokemonUseCase, which carries its own state guard — so an id that is already
// replicated is a 409 here rather than a silent second copy.
//
// Not @Transactional: the catalogue call is remote I/O and the write is a single save, so a
// transaction spanning both would hold a connection open across the network.
@Service
public class SyncPokemonUseCase {

    private static final Pattern NUMERIC = Pattern.compile("\\d++");

    private final UpstreamPokemonSource upstreamSource;
    private final PokemonRepository repository;
    private final ClockPort clock;

    public SyncPokemonUseCase(UpstreamPokemonSource upstreamSource, PokemonRepository repository, ClockPort clock) {
        this.upstreamSource = upstreamSource;
        this.repository = repository;
        this.clock = clock;
    }

    public Pokemon sync(String idOrName) {
        String reference = requireReference(idOrName);
        UpstreamPokemon upstream =
                fetch(reference).orElseThrow(() -> new PokemonNotFoundUpstreamException(reference));
        requireNotAlreadyReplicated(upstream.pokeApiId());
        return repository.save(replicate(upstream, clock.now()));
    }

    // PENDING is where a replication enters the lifecycle, and SYNCED is the one edge that
    // writes upstream data into a record that has no curator data yet
    private static Pokemon replicate(UpstreamPokemon upstream, Instant now) {
        Pokemon fresh = Pokemon.pending(upstream.pokeApiId(), upstream.replicated());
        fresh.transitionTo(ReplicationState.SYNCED, now);
        return fresh;
    }

    private Optional<UpstreamPokemon> fetch(String reference) {
        return NUMERIC.matcher(reference).matches()
                ? upstreamSource.fetchById(PokeApiId.of(Integer.parseInt(reference)))
                : upstreamSource.fetchByName(new PokemonName(reference));
    }

    private void requireNotAlreadyReplicated(PokeApiId pokeApiId) {
        if (repository.existsByPokeApiId(pokeApiId)) {
            throw new DuplicatePokemonException(pokeApiId);
        }
    }

    private static String requireReference(String idOrName) {
        String reference = idOrName == null ? "" : idOrName.strip();
        if (reference.isEmpty()) {
            throw new InvalidPokemonDataException("idOrName must not be blank");
        }
        return reference;
    }
}

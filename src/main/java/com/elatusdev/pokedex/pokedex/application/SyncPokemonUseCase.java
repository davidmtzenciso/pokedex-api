package com.elatusdev.pokedex.pokedex.application;

import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import com.elatusdev.pokedex.catalog.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.pokedex.domain.DuplicatePokemonException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
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

    private final PokemonCatalog catalog;
    private final PokemonRepository repository;
    private final ClockPort clock;

    public SyncPokemonUseCase(PokemonCatalog catalog, PokemonRepository repository, ClockPort clock) {
        this.catalog = catalog;
        this.repository = repository;
        this.clock = clock;
    }

    public Pokemon sync(String idOrName) {
        String reference = requireReference(idOrName);
        CatalogPokemon upstream =
                fetch(reference).orElseThrow(() -> new PokemonNotFoundUpstreamException(reference));
        PokeApiId pokeApiId = upstream
                .pokeApiId()
                .orElseThrow(() -> new InvalidPokemonDataException(
                        "the catalogue returned no upstream id for '" + reference + "'"));
        requireNotAlreadyReplicated(pokeApiId);
        return repository.save(replicate(pokeApiId, upstream, clock.now()));
    }

    // PENDING is where a replication enters the lifecycle, and SYNCED is the one edge that
    // writes upstream data into a record that has no curator data yet
    private static Pokemon replicate(PokeApiId pokeApiId, CatalogPokemon upstream, Instant now) {
        Pokemon fresh = Pokemon.pending(pokeApiId, upstream.replicated());
        fresh.transitionTo(ReplicationState.SYNCED, now);
        return fresh;
    }

    private Optional<CatalogPokemon> fetch(String reference) {
        return NUMERIC.matcher(reference).matches()
                ? catalog.fetchById(PokeApiId.of(Integer.parseInt(reference)))
                : catalog.fetchByName(new PokemonName(reference));
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

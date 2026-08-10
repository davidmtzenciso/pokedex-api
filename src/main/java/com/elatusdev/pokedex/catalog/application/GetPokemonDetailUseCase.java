package com.elatusdev.pokedex.catalog.application;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.catalog.domain.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.catalog.domain.PokemonCatalog;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import com.elatusdev.pokedex.shared.domain.PokemonName;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

// Not @Transactional: the catalogue call is remote I/O, and it costs three upstream
// requests — pokemon, species, evolution chain.
@Service
public class GetPokemonDetailUseCase {

    private static final Pattern NUMERIC = Pattern.compile("\\d++");

    private final PokemonCatalog catalog;
    private final PokemonRepository repository;
    private final UpstreamOutagePolicy outagePolicy;

    public GetPokemonDetailUseCase(
            PokemonCatalog catalog, PokemonRepository repository, UpstreamOutagePolicy outagePolicy) {
        this.catalog = catalog;
        this.repository = repository;
        this.outagePolicy = outagePolicy;
    }

    public PokemonDetailResult detail(String idOrName) {
        String reference = requireReference(idOrName);
        return outagePolicy.applyTo(
                () -> new PokemonDetailResult(fromCatalogue(reference), false),
                () -> fromReplica(reference).map(pokemon -> new PokemonDetailResult(pokemon, true)));
    }

    private Pokemon fromCatalogue(String reference) {
        return upstream(reference).orElseThrow(() -> new PokemonNotFoundUpstreamException(reference));
    }

    private Optional<Pokemon> upstream(String reference) {
        return isUpstreamId(reference)
                ? catalog.fetchById(PokeApiId.of(Integer.parseInt(reference)))
                : catalog.fetchByName(new PokemonName(reference));
    }

    private Optional<Pokemon> fromReplica(String reference) {
        return isUpstreamId(reference)
                ? repository.findByPokeApiId(PokeApiId.of(Integer.parseInt(reference)))
                : repository.findByName(new PokemonName(reference));
    }

    private static String requireReference(String idOrName) {
        String reference = idOrName == null ? "" : idOrName.strip();
        if (reference.isEmpty()) {
            throw new InvalidPokemonDataException("idOrName must not be blank");
        }
        return reference;
    }

    private static boolean isUpstreamId(String reference) {
        return NUMERIC.matcher(reference).matches();
    }
}

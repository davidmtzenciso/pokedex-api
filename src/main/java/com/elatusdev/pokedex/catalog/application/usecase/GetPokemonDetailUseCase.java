package com.elatusdev.pokedex.catalog.application.usecase;

import com.elatusdev.pokedex.catalog.application.result.PokemonDetailResult;
import com.elatusdev.pokedex.shared.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.catalog.domain.exception.PokemonNotFoundUpstreamException;
import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.catalog.domain.port.PokemonCatalog;
import com.elatusdev.pokedex.pokedex.domain.port.PokemonRepository;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import com.elatusdev.pokedex.shared.domain.vo.PokemonName;
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

    // blank is malformed input, not an absent Pokemon; the contract says minLength 1
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

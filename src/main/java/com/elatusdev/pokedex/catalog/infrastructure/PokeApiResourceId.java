package com.elatusdev.pokedex.catalog.infrastructure;

import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import com.elatusdev.pokedex.shared.domain.PokeApiId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Upstream identifies related resources only by URL, so the id has to be read back out of
// the path: .../pokemon-species/133/ -> 133
final class PokeApiResourceId {

    private static final Pattern TRAILING_ID = Pattern.compile("/(\\d++)/?$");

    private PokeApiResourceId() {}

    static PokeApiId of(PokeApiNameRef reference) {
        Matcher matcher = TRAILING_ID.matcher(reference.url());
        if (!matcher.find()) {
            throw new InvalidPokemonDataException("cannot read a resource id from " + reference.url());
        }
        return PokeApiId.of(Integer.parseInt(matcher.group(1)));
    }
}

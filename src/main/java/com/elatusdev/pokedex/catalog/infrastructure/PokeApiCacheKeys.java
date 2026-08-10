package com.elatusdev.pokedex.catalog.infrastructure;

import com.elatusdev.pokedex.shared.domain.PokeApiId;

// Public because the sync use cases evict through these keys too. Duplicating the format
// there would let the cache and the evictor drift apart silently.
public final class PokeApiCacheKeys {

    public static final String PAGE_PREFIX = "pokeapi:page:";

    private PokeApiCacheKeys() {}

    public static String page(int offset, int limit) {
        return PAGE_PREFIX + offset + ":" + limit;
    }

    public static String pokemon(PokeApiId pokeApiId) {
        return "pokeapi:pokemon:" + pokeApiId.value();
    }

    public static String pokemon(String idOrName) {
        return "pokeapi:pokemon:" + idOrName;
    }

    public static String species(int speciesId) {
        return "pokeapi:species:" + speciesId;
    }

    public static String evolution(int chainId) {
        return "pokeapi:evolution:" + chainId;
    }
}

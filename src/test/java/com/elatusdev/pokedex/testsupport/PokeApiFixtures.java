package com.elatusdev.pokedex.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import com.elatusdev.pokedex.catalog.infrastructure.PokeApiSpeciesResponse;
import com.elatusdev.pokedex.catalog.infrastructure.PokeApiPokemonResponse;
import com.elatusdev.pokedex.catalog.infrastructure.PokeApiListResponse;
import com.elatusdev.pokedex.catalog.infrastructure.PokeApiEvolutionChainResponse;

// Real recorded PokeAPI payloads, trimmed to the fields the mapper reads. Trimmed rather
// than synthesised: genera[0] and names[0] really are Japanese, and flavor_text really does
// carry \n and \f — a hand-written fixture would quietly lose the traps under test.
public final class PokeApiFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private PokeApiFixtures() {}

    public static PokeApiPokemonResponse pokemon1() {
        return read("pokemon-1.json", PokeApiPokemonResponse.class);
    }

    public static PokeApiSpeciesResponse species1() {
        return read("species-1.json", PokeApiSpeciesResponse.class);
    }

    public static PokeApiEvolutionChainResponse evolutionChain1() {
        return read("evolution-chain-1.json", PokeApiEvolutionChainResponse.class);
    }

    public static PokeApiEvolutionChainResponse evolutionChain67() {
        return read("evolution-chain-67.json", PokeApiEvolutionChainResponse.class);
    }

    public static PokeApiListResponse pokemonList() {
        return read("pokemon-list.json", PokeApiListResponse.class);
    }

    public static String raw(String name) {
        try (InputStream in = open(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read fixture " + name, failure);
        }
    }

    private static <T> T read(String name, Class<T> type) {
        try (InputStream in = open(name)) {
            return MAPPER.readValue(in, type);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read fixture " + name, failure);
        }
    }

    private static InputStream open(String name) {
        InputStream in = PokeApiFixtures.class.getResourceAsStream("/pokeapi/" + name);
        if (in == null) {
            throw new UncheckedIOException(new IOException("fixture not on the test classpath: " + name));
        }
        return in;
    }
}

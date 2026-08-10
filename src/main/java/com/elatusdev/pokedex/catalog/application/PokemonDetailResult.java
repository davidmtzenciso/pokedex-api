package com.elatusdev.pokedex.catalog.application;

import com.elatusdev.pokedex.pokedex.domain.Pokemon;

public record PokemonDetailResult(Pokemon pokemon, boolean stale) {}

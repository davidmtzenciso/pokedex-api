package com.elatusdev.pokedex.catalog.application.result;

import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;

public record PokemonDetailResult(Pokemon pokemon, boolean stale) {}

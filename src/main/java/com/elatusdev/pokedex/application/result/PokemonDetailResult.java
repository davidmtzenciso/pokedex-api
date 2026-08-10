package com.elatusdev.pokedex.application.result;

import com.elatusdev.pokedex.domain.model.Pokemon;

public record PokemonDetailResult(Pokemon pokemon, boolean stale) {}

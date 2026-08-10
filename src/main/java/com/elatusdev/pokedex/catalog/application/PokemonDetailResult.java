package com.elatusdev.pokedex.catalog.application;
import com.elatusdev.pokedex.catalog.domain.CatalogPokemon;



public record PokemonDetailResult(CatalogPokemon pokemon, boolean stale) {}

package com.elatusdev.pokedex.catalog.infrastructure.pokeapi;

// IA1: the list endpoint carries only these two fields, which is the whole reason a page
// costs 1 + 2N upstream calls
public record PokeApiNameRef(String name, String url) {}

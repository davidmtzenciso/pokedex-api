package com.elatusdev.pokedex.catalog.infrastructure;

import java.util.List;

// IA1: results carry only {name, url}. Everything a list row needs comes from the fan-out.
public record PokeApiListResponse(int count, List<PokeApiNameRef> results) {}

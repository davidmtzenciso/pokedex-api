// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.infrastructure.pokeapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// IA5: evolves_to nests arbitrarily deep and branches — Eevee has eight children. The
// recursion is in the shape of this type, not an afterthought in the mapper.
public record PokeApiEvolutionChainResponse(int id, ChainLink chain) {

    public record ChainLink(
            PokeApiNameRef species,
            @JsonProperty("evolution_details") List<EvolutionDetail> evolutionDetails,
            @JsonProperty("evolves_to") List<ChainLink> evolvesTo) {}

    public record EvolutionDetail(PokeApiNameRef trigger, @JsonProperty("min_level") Integer minLevel) {}
}

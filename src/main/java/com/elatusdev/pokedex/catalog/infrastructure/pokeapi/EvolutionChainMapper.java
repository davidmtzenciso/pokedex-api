package com.elatusdev.pokedex.catalog.infrastructure.pokeapi;

import com.elatusdev.pokedex.pokedex.domain.model.EvolutionLink;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Flattens the recursive upstream tree into the edge list the domain models. A loop over
// two levels passes for Bulbasaur and truncates every branching family — IA5.
public class EvolutionChainMapper {

    private static final String UNSPECIFIED_TRIGGER = "unspecified";

    public List<EvolutionLink> flatten(PokeApiEvolutionChainResponse chain) {
        List<EvolutionLink> links = new ArrayList<>();
        collect(chain.chain(), links);
        return List.copyOf(links);
    }

    private void collect(PokeApiEvolutionChainResponse.ChainLink node, List<EvolutionLink> links) {
        PokeApiId from = PokeApiResourceId.of(node.species());
        for (PokeApiEvolutionChainResponse.ChainLink child : children(node)) {
            links.add(edge(from, child));
            collect(child, links);
        }
    }

    private static EvolutionLink edge(PokeApiId from, PokeApiEvolutionChainResponse.ChainLink child) {
        Optional<PokeApiEvolutionChainResponse.EvolutionDetail> detail = firstDetail(child);
        return new EvolutionLink(
                from,
                PokeApiResourceId.of(child.species()),
                detail.map(d -> d.trigger().name()).orElse(UNSPECIFIED_TRIGGER),
                detail.map(PokeApiEvolutionChainResponse.EvolutionDetail::minLevel));
    }

    private static Optional<PokeApiEvolutionChainResponse.EvolutionDetail> firstDetail(
            PokeApiEvolutionChainResponse.ChainLink child) {
        return child.evolutionDetails() == null || child.evolutionDetails().isEmpty()
                ? Optional.empty()
                : Optional.of(child.evolutionDetails().get(0));
    }

    private static List<PokeApiEvolutionChainResponse.ChainLink> children(
            PokeApiEvolutionChainResponse.ChainLink node) {
        return node.evolvesTo() == null ? List.of() : node.evolvesTo();
    }
}

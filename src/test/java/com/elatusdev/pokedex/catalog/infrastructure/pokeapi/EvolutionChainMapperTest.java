package com.elatusdev.pokedex.catalog.infrastructure.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.pokedex.domain.model.EvolutionLink;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.testsupport.PokeApiFixtures;

class EvolutionChainMapperTest {

    private final EvolutionChainMapper mapper = new EvolutionChainMapper();

    // IA5 — the chain is a recursive tree, not a list. Eevee is the case a flat mapper
    // truncates silently, which is why it is the named fixture rather than a nice-to-have.
    @Test
    void should_flatten_all_eight_branches_when_the_species_is_eevee() {
        List<EvolutionLink> links = mapper.flatten(PokeApiFixtures.evolutionChain67());

        assertThat(links).hasSize(8);
        assertThat(links).allSatisfy(link -> assertThat(link.from()).isEqualTo(PokeApiId.of(133)));
        assertThat(links.stream().map(link -> link.to().value()))
                .containsExactlyInAnyOrder(134, 135, 136, 196, 197, 470, 471, 700);
    }

    @Test
    void should_carry_the_trigger_when_the_branch_uses_an_item() {
        List<EvolutionLink> links = mapper.flatten(PokeApiFixtures.evolutionChain67());

        assertThat(links)
                .filteredOn(link -> link.to().equals(PokeApiId.of(134)))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.trigger()).isEqualTo("use-item");
                    assertThat(link.minLevel()).isEmpty();
                });
    }

    // the recursion has to survive more than one level, which Eevee alone does not prove
    @Test
    void should_flatten_every_level_when_the_chain_is_linear() {
        List<EvolutionLink> links = mapper.flatten(PokeApiFixtures.evolutionChain1());

        assertThat(links).hasSize(2);
        assertThat(links.get(0).from()).isEqualTo(PokeApiId.of(1));
        assertThat(links.get(0).to()).isEqualTo(PokeApiId.of(2));
        assertThat(links.get(1).from()).isEqualTo(PokeApiId.of(2));
        assertThat(links.get(1).to()).isEqualTo(PokeApiId.of(3));
    }

    @Test
    void should_carry_the_minimum_level_when_the_branch_is_level_based() {
        List<EvolutionLink> links = mapper.flatten(PokeApiFixtures.evolutionChain1());

        assertThat(links.get(0).trigger()).isEqualTo("level-up");
        assertThat(links.get(0).minLevel()).contains(16);
    }

    // F12 — the evolution graph is acyclic. Upstream ships a tree, so this asserts the
    // flattening does not invent a back-edge: no species is reachable from itself.
    @Test
    void should_produce_an_acyclic_edge_list() {
        assertAcyclic(mapper.flatten(PokeApiFixtures.evolutionChain67()));
        assertAcyclic(mapper.flatten(PokeApiFixtures.evolutionChain1()));
    }

    private static void assertAcyclic(List<EvolutionLink> links) {
        Map<Integer, List<Integer>> outgoing = links.stream()
                .collect(Collectors.groupingBy(
                        link -> link.from().value(), Collectors.mapping(link -> link.to().value(), Collectors.toList())));
        for (Integer start : outgoing.keySet()) {
            Deque<Integer> pending = new ArrayDeque<>(outgoing.get(start));
            Set<Integer> reachable = new HashSet<>();
            while (!pending.isEmpty()) {
                Integer next = pending.pop();
                assertThat(next).as("species %s is reachable from itself", start).isNotEqualTo(start);
                if (reachable.add(next)) {
                    pending.addAll(outgoing.getOrDefault(next, List.of()));
                }
            }
        }
    }

    @Test
    void should_return_an_empty_edge_list_when_the_species_never_evolves() {
        PokeApiEvolutionChainResponse chain = new PokeApiEvolutionChainResponse(
                999,
                new PokeApiEvolutionChainResponse.ChainLink(
                        new PokeApiNameRef("tauros", "https://pokeapi.co/api/v2/pokemon-species/128/"),
                        List.of(),
                        List.of()));

        assertThat(mapper.flatten(chain)).isEmpty();
    }
}

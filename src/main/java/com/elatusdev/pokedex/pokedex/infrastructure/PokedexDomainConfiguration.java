package com.elatusdev.pokedex.pokedex.infrastructure;

import com.elatusdev.pokedex.pokedex.domain.PokemonMergePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// pokedex/infrastructure returns, which ADR-0013's amendment said would happen the moment
// something here needed a framework. This is that moment and nothing more.
//
// PokemonMergePolicy carries no annotation because it is a domain class and L2 forbids one;
// annotating it would put Spring inside the one package that depends on nothing, to save
// this file. The wiring belongs in the outermost ring instead.
@Configuration(proxyBeanMethods = false)
public class PokedexDomainConfiguration {

    @Bean
    PokemonMergePolicy pokemonMergePolicy() {
        return new PokemonMergePolicy();
    }
}

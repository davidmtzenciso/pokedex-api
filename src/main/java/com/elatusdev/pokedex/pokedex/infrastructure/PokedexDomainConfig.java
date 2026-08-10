package com.elatusdev.pokedex.pokedex.infrastructure;

import com.elatusdev.pokedex.pokedex.domain.PokemonMergePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// pokedex/infrastructure returns here for the reason ADR-0013 said it would: something in
// this context now needs a framework. PokemonMergePolicy cannot carry @Component — it lives
// in domain, and L2 forbids a Spring annotation there — so the wiring lives outside it.
@Configuration(proxyBeanMethods = false)
public class PokedexDomainConfig {

    @Bean
    public PokemonMergePolicy pokemonMergePolicy() {
        return new PokemonMergePolicy();
    }
}

// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.infrastructure.pokeapi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// scoped to this package rather than a @ConfigurationPropertiesScan on the application
// class, which three parallel streams would otherwise all be editing
@Configuration
@EnableConfigurationProperties(PokeApiProperties.class)
public class PokeApiConfig {

    @Bean
    public PokeApiClient pokeApiClient(PokeApiProperties properties) {
        return new PokeApiClient(properties);
    }

    @Bean
    public PokeApiMapper pokeApiMapper() {
        return new PokeApiMapper();
    }

    @Bean
    public EvolutionChainMapper evolutionChainMapper() {
        return new EvolutionChainMapper();
    }

    @Bean
    public PokeApiCatalogAdapter pokeApiCatalogAdapter(
            PokeApiClient client,
            PokeApiMapper mapper,
            EvolutionChainMapper evolutionMapper,
            PokeApiProperties properties) {
        return new PokeApiCatalogAdapter(client, mapper, evolutionMapper, properties);
    }
}

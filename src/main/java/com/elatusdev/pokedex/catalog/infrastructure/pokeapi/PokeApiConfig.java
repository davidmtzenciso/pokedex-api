package com.elatusdev.pokedex.catalog.infrastructure.pokeapi;

import com.elatusdev.pokedex.shared.port.CachePort;
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
            PokeApiProperties properties,
            CachePort cache) {
        return new PokeApiCatalogAdapter(client, mapper, evolutionMapper, properties, cache);
    }
}

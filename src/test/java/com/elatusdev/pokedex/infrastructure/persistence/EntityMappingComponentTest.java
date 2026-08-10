package com.elatusdev.pokedex.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

// This context reaching a started state is itself the assertion: ddl-auto is validate, so
// any column the mapping and the migration disagree about fails the refresh. The tests below
// make the two things that guarantee it explicit, because both are silent when wrong —
// ddl-auto: update would paper over drift, and an unmapped entity would never be checked.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EntityMappingComponentTest {

    private static final List<String> EXPECTED_ENTITIES = List.of(
            "EvolutionLinkDataModel",
            "LocalizedNameDataModel",
            "PokemonAbilityDataModel",
            "PokemonDataModel",
            "PokemonStatDataModel",
            "PokemonTagDataModel",
            "PokemonTypeDataModel",
            "UserDataModel");

    private final EntityManagerFactory entityManagerFactory;
    private final Environment environment;

    EntityMappingComponentTest(
            @Autowired EntityManagerFactory entityManagerFactory, @Autowired Environment environment) {
        this.entityManagerFactory = entityManagerFactory;
        this.environment = environment;
    }

    @Test
    void should_validate_against_the_migrated_schema_when_hibernate_starts() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void should_map_every_table_flyway_creates_except_the_ones_another_work_unit_owns() {
        List<String> mapped = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .sorted()
                .toList();

        assertThat(mapped).isEqualTo(EXPECTED_ENTITIES);
    }
}

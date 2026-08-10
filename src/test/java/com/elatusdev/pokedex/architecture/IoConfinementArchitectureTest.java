package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.properties.HasName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IoConfinementArchitectureTest {

    private static final DescribedPredicate<HasName> PERSISTENCE_API = nameMatching(
                    "jakarta\\.persistence\\.EntityManager"
                            + "|org\\.springframework\\.jdbc\\.core\\.JdbcTemplate"
                            + "|org\\.springframework\\.data\\.jpa\\.repository\\.JpaRepository")
            .as("EntityManager, JdbcTemplate or JpaRepository");

    // the builder is a nested type, and reaching it is the usual way this rule gets dodged
    private static final DescribedPredicate<HasName> REST_CLIENT =
            nameMatching("org\\.springframework\\.web\\.client\\.RestClient(\\$.*)?").as("RestClient");

    @Test
    void should_reject_persistence_api_dependencies_when_the_class_is_outside_infrastructure() {
        noClasses()
                .that().resideOutsideOfPackages("..infrastructure..", "..interfaces..")
                .should().dependOnClassesThat(PERSISTENCE_API)
                .because("IO1 — database access lives behind a repository adapter in the interfaces ring, never in a use case")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_rest_client_dependencies_when_the_class_is_outside_the_pokeapi_adapter() {
        noClasses()
                .that().resideOutsideOfPackage("..catalog.infrastructure..")
                .should().dependOnClassesThat(REST_CLIENT)
                .because("IO2 — one adapter owns the upstream call, so its timeouts, retries and fan-out bound live in one place")
                .check(ProjectClasses.production());
    }
}

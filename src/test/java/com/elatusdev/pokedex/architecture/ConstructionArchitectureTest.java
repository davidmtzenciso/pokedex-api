package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ConstructionArchitectureTest {

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String VALUE = "org.springframework.beans.factory.annotation.Value";

    @Test
    void should_reject_autowired_when_it_annotates_a_field() {
        noFields()
                .should().beAnnotatedWith(AUTOWIRED)
                .because("CI1 — field injection hides a dependency from the constructor, so the class lies about what it needs")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_value_when_it_annotates_a_field() {
        noFields()
                .should().beAnnotatedWith(VALUE)
                .because("CI1 — configuration arrives as a @ConfigurationProperties record, not as @Value scattered across fields")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_autowired_when_it_annotates_a_setter() {
        noMethods()
                .that().haveNameMatching("set[A-Z].*")
                .should().beAnnotatedWith(AUTOWIRED)
                .because("CI1 — setter injection leaves a window in which the object exists but is not usable")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }
}

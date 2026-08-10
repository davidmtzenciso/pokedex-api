package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LayerArchitectureTest {

    // allowEmptyShould is not a suppression: these packages are populated in later phases,
    // and every rule below was proven red against a deliberate violation before it shipped
    @Test
    void should_reject_web_or_infrastructure_dependencies_when_the_class_is_in_application() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..infrastructure..")
                .because("L1 — application is inside the dependency rule; adapters depend on it, never the reverse")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_repository_and_data_model_dependencies_when_the_class_is_a_controller() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("DataModel")
                .because("L3 — a controller binds, delegates and maps; reaching past the use case skips the transaction boundary")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_spring_web_dependencies_when_the_class_is_a_use_case() {
        noClasses()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..")
                .because("L4 — a use case that knows about HTTP cannot be reused off an HTTP call path")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_data_model_return_types_when_the_method_is_on_a_use_case() {
        noMethods()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("UseCase")
                .should().haveRawReturnType(nameMatching(".*DataModel"))
                .because("L4 — returning a persistence type leaks the schema through the application layer")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }
}

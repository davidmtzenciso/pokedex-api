// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OpenApiContractConfinementArchitectureTest {

    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";

    private static final DescribedPredicate<JavaClass> GENERATED_API = resideInAPackage(
                    ProjectClasses.GENERATED_API_PACKAGE + "..")
            .and(nameMatching(".*Api"))
            .as("a generated *Api interface in " + ProjectClasses.GENERATED_API_PACKAGE);

    @Test
    void should_implement_a_generated_api_when_the_class_is_a_rest_controller() {
        classes()
                .that().areAnnotatedWith(REST_CONTROLLER)
                .should().implement(GENERATED_API)
                .because("OA1 — the spec precedes the controller; a hand-written endpoint is an undocumented endpoint")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }
}

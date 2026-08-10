// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NamingConventionArchitectureTest {

    @Test
    void should_reside_in_application_usecase_when_the_class_is_a_use_case() {
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..application.usecase..")
                .because("N1 — a class's package tells you what it may touch")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_web_controller_when_the_class_is_a_controller() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..web.controller..")
                .because("N2 — a class's package tells you what it may touch")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_a_port_or_persistence_package_when_the_class_is_a_repository() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .should().resideInAnyPackage("..domain.port..", "..infrastructure.persistence..")
                .because("N3 — the port is domain-owned, the adapter is infrastructure-owned, and nothing else is a repository")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_be_an_interface_when_the_repository_is_a_domain_port() {
        classes()
                .that().haveSimpleNameEndingWith("Repository").and().resideInAPackage("..domain.port..")
                .should().beInterfaces()
                .because("N3 — a port states what the domain needs; an implementation there would be an adapter in the wrong layer")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_persistence_model_when_the_class_is_a_data_model() {
        classes()
                .that().haveSimpleNameEndingWith("DataModel")
                .should().resideInAPackage("..infrastructure.persistence.model..")
                .because("N4 — persistence types stay behind the repository adapter")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_the_exception_package_when_the_class_is_a_domain_exception() {
        classes()
                .that().haveSimpleNameEndingWith("Exception").and().resideInAPackage("..domain..")
                .should().resideInAPackage("..domain.exception..")
                .because("N5 — one type per failure mode, all in one place, because each maps to a distinct response code")
                .check(ProjectClasses.production());
    }

    @Test
    void should_extend_runtime_exception_when_the_class_is_in_the_domain_exception_package() {
        classes()
                .that().resideInAPackage("..domain.exception..")
                .should().beAssignableTo(RuntimeException.class)
                .because("N5 — a checked domain exception would force every caller to know about a failure the advice already translates")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_service_named_classes_when_they_reside_in_usecase() {
        noClasses()
                .that().resideInAPackage("..usecase..")
                .should().haveSimpleNameEndingWith("Service")
                .because("N5 — one class per operation; a *Service inside usecase is the god-object this structure exists to prevent")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }
}

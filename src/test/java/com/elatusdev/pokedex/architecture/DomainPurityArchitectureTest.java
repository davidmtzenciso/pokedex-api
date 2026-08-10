package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.shared.domain.CachePort;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DomainPurityArchitectureTest {

    private static final String[] FRAMEWORKS = {
        "org.springframework..",
        "jakarta..",
        "javax..",
        "org.hibernate..",
        "com.fasterxml.jackson..",
        "org.slf4j..",
        "ch.qos.logback..",
    };

    // relative, not absolute: every context carries its own application/infrastructure/web,
    // so com.elatusdev.pokedex.application.. no longer names anything — ADR-0013
    private static final String[] OTHER_LAYERS = {
        "..application..", "..infrastructure..",
    };

    // L2 is the whole reason this suite is not optional. One Maven module puts every
    // dependency on the domain's classpath, so the compiler accepts an import that the
    // architecture forbids — ADR-0001.
    @Test
    void should_reject_framework_dependencies_when_the_class_is_in_domain() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .because("L2 — a framework type in the domain is a design error, not a missing dependency: define a port")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_other_layer_dependencies_when_the_class_is_in_domain() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(OTHER_LAYERS)
                .because("L2 — the domain is the innermost layer and depends on nothing")
                .check(ProjectClasses.production());
    }

    // L5 — technical ports (CachePort, ClockPort) sit outside ..domain.. because they name no
    // domain type, so L2 does not reach them. They are still ports: the abstraction the inside
    // depends on. A framework type here would put Spring back on the inward side — ADR-0013
    @Test
    void should_reject_framework_dependencies_when_the_class_is_a_technical_port() {
        noClasses()
                .that().resideInAPackage("..shared.port..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .because("L5 — a port names what the inside needs; the framework belongs in the adapter")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }
}

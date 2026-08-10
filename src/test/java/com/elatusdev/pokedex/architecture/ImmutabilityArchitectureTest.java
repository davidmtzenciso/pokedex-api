// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.beRecords;
import static com.tngtech.archunit.lang.conditions.ArchConditions.haveOnlyFinalFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ImmutabilityArchitectureTest {

    // the enum members of ..domain.vo.. satisfy this through haveOnlyFinalFields, not beRecords
    @Test
    void should_be_a_record_or_have_only_final_fields_when_the_class_is_a_value_object() {
        classes()
                .that().resideInAPackage("..domain.vo..")
                .should(beRecords().or(haveOnlyFinalFields()))
                .because("IMF1 — a value object with a mutable field has identity, and identity is what a value object is defined as not having")
                .check(ProjectClasses.production());
    }
}

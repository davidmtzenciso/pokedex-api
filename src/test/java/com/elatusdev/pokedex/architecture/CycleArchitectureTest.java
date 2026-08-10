// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CycleArchitectureTest {

    // sliced at layer granularity, not per package: ReplicationState -> IllegalStateTransitionException
    // -> ReplicationState is a legitimate cycle between domain.model and domain.exception, and an
    // aggregate that cannot name the exception it throws is the wrong trade
    @Test
    void should_be_free_of_cycles_when_slicing_by_layer() {
        slices()
                .matching("com.elatusdev.pokedex.(*)..")
                .should().beFreeOfCycles()
                .because("CY1 — a package cycle compiles fine and makes every later extraction a rewrite")
                .check(ProjectClasses.production());
    }
}

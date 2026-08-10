package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.pokedex.domain.IllegalStateTransitionException;
import com.elatusdev.pokedex.pokedex.domain.ReplicationState;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CycleArchitectureTest {

    // the first segment is now the bounded context, not the layer — ADR-0013
    @Test
    void should_be_free_of_cycles_when_slicing_by_bounded_context() {
        slices()
                .matching("com.elatusdev.pokedex.(*)..")
                .should().beFreeOfCycles()
                .because("CY1 — a cycle between contexts means neither can be extracted or reasoned about alone")
                .check(ProjectClasses.production());
    }

    // sliced at layer granularity, not per package: ReplicationState -> IllegalStateTransitionException
    // -> ReplicationState is a legitimate cycle between domain.model and domain.exception, and an
    // aggregate that cannot name the exception it throws is the wrong trade
    @Test
    void should_be_free_of_cycles_when_slicing_by_layer_within_a_context() {
        slices()
                .matching("com.elatusdev.pokedex.*.(*)..")
                .should().beFreeOfCycles()
                .because("CY2 — a package cycle compiles fine and makes every later extraction a rewrite")
                .check(ProjectClasses.production());
    }
}

package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoundedContextArchitectureTest {

    private static final String CATALOG = "..pokedex.catalog..";
    private static final String POKEDEX = "..pokedex.pokedex..";
    private static final String IDENTITY = "..pokedex.identity..";
    private static final String SHARED = "..pokedex.shared..";

    // BC1 — identity is the one context with no Pokemon in it. If it ever reaches for the
    // catalogue or the collection, the auxiliary API has stopped being auxiliary
    @Test
    void should_reject_catalog_and_pokedex_dependencies_when_the_class_is_in_identity() {
        noClasses()
                .that().resideInAPackage(IDENTITY)
                .should().dependOnClassesThat().resideInAnyPackage(CATALOG, POKEDEX)
                .because("BC1 — identity knows about users and tokens, never about Pokemon")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    // BC2 — the catalogue is a read-through view of upstream. It has no local store and no
    // notion of a curator, so it must not reach into identity
    @Test
    void should_reject_identity_dependencies_when_the_class_is_in_catalog() {
        noClasses()
                .that().resideInAPackage(CATALOG)
                .should().dependOnClassesThat().resideInAnyPackage(IDENTITY)
                .because("BC2 — the catalogue serves anonymous reads; authorisation is decided at the edge")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    // BC3 — the load-bearing one. shared is a kernel, not a dumping ground: the moment it
    // depends on a context, every context inherits that context transitively and the
    // decomposition is decorative — ADR-0013
    @Test
    void should_reject_all_context_dependencies_when_the_class_is_in_shared() {
        noClasses()
                .that().resideInAPackage(SHARED)
                .should().dependOnClassesThat().resideInAnyPackage(CATALOG, POKEDEX, IDENTITY)
                .because("BC3 — the shared kernel depends on nothing; anything context-specific does not belong in it")
                .check(ProjectClasses.production());
    }

    // BC4 — a context reaches another context only through its domain. Importing someone
    // else's use case, adapter or controller couples you to how they work, not to what they mean
    @Test
    void should_reject_cross_context_application_infrastructure_and_web_dependencies() {
        noClasses()
                .that().resideInAPackage(CATALOG)
                .should().dependOnClassesThat()
                .resideInAnyPackage("..pokedex.pokedex.application..", "..pokedex.pokedex.infrastructure..",
                        "..pokedex.pokedex.infrastructure..", "..pokedex.identity.application..",
                        "..pokedex.identity.infrastructure..", "..pokedex.identity.web..")
                .because("BC4 — cross-context coupling goes through the domain or not at all")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    // BC5 — the catalogue read path must not know the curated aggregate. It reads
    // replicated data, and Pokemon carries region, notes, tags, replication state and a
    // local id that an anonymous read has no business seeing.
    //
    // Two adapters bridge the contexts, and BOTH point catalog -> pokedex. That direction is
    // the rule, not an accident: replication needs upstream data, and had pokedex imported
    // the catalogue to get it, the two contexts would point at each other and CY1 would go
    // red. Each context declares what it needs as its own port and the bridge maps onto it.
    //
    //   PokedexLocalReplicaAdapter    satisfies catalog's LocalReplica from the aggregate
    //   PokedexUpstreamSourceAdapter  satisfies pokedex's UpstreamPokemonSource from the catalogue
    @Test
    void should_reach_pokedex_only_through_an_anti_corruption_adapter_when_the_class_is_in_catalog() {
        noClasses()
                .that().resideInAPackage(CATALOG)
                .and().haveSimpleNameNotEndingWith("PokedexLocalReplicaAdapter")
                .and().haveSimpleNameNotEndingWith("PokedexUpstreamSourceAdapter")
                .should().dependOnClassesThat().resideInAPackage("..pokedex.pokedex..")
                .because("BC5 — two adapters bridge catalog and pokedex, both pointing catalog -> pokedex; everything else goes through its own model")
                .check(ProjectClasses.production());
    }

    // BC6 — the other half of BC5, and the one that keeps the graph acyclic. pokedex states
    // what it needs from upstream in its own vocabulary; the catalogue satisfies it. A single
    // import of ..catalog.. from this context would restore the cycle BC5's direction avoids.
    @Test
    void should_never_depend_on_catalog_when_the_class_is_in_pokedex() {
        noClasses()
                .that().resideInAPackage(POKEDEX)
                .should().dependOnClassesThat().resideInAPackage(CATALOG)
                .because("BC6 — replication declares UpstreamPokemonSource and catalog implements it, so both bridges point one way")
                .check(ProjectClasses.production());
    }
}

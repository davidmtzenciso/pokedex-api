package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import com.elatusdev.pokedex.pokedex.interfaces.PokemonDataModel;
import com.elatusdev.pokedex.pokedex.interfaces.JpaPokemonRepositoryAdapter;
import com.elatusdev.pokedex.pokedex.domain.PokemonRepository;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;
import com.elatusdev.pokedex.catalog.domain.CatalogPage;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NamingConventionArchitectureTest {

    @Test
    void should_reside_in_application_usecase_when_the_class_is_a_use_case() {
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..application..")
                .because("N1 — a class's package tells you what it may touch")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_web_controller_when_the_class_is_a_controller() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..interfaces..")
                .because("N2 — a class's package tells you what it may touch")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_a_port_or_persistence_package_when_the_class_is_a_repository() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .should().resideInAnyPackage("..domain..", "..interfaces..")
                .because("N3 — the port is domain-owned, the adapter belongs to the interfaces ring, and nothing else is a repository")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_be_an_interface_when_the_repository_is_a_domain_port() {
        classes()
                .that().haveSimpleNameEndingWith("Repository").and().resideInAPackage("..domain..")
                .should().beInterfaces()
                .because("N3 — a port states what the domain needs; an implementation there would be an adapter in the wrong layer")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_persistence_model_when_the_class_is_a_data_model() {
        classes()
                .that().haveSimpleNameEndingWith("DataModel")
                .should().resideInAPackage("..interfaces..")
                .because("N4 — persistence types sit in the interfaces ring, behind the repository adapter")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_the_exception_package_when_the_class_is_a_domain_exception() {
        classes()
                .that().haveSimpleNameEndingWith("Exception").and().resideInAPackage("..domain..")
                .should().resideInAPackage("..domain..")
                .because("N5 — one type per failure mode, all in one place, because each maps to a distinct response code")
                .check(ProjectClasses.production());
    }

    @Test
    void should_extend_runtime_exception_when_the_class_is_in_the_domain_exception_package() {
        classes()
                .that().haveSimpleNameEndingWith("Exception").and().resideInAPackage("..domain..")
                .should().beAssignableTo(RuntimeException.class)
                .because("N5 — a checked domain exception would force every caller to know about a failure the advice already translates")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reject_service_named_classes_when_they_reside_in_usecase() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().haveSimpleNameEndingWith("Service")
                .because("N5 — one class per operation; a *Service inside usecase is the god-object this structure exists to prevent")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    // N6 has two halves and needs both. The generator already suffixes what it emits, so the
    // half that earns its keep is the second: it catches a hand-written PokemonResponse that
    // never passed through the contract at all
    @Test
    void should_end_with_dto_when_the_class_is_in_the_dto_package() {
        classes()
                .that().resideInAPackage("..contract.dto..")
                .should().haveSimpleNameEndingWith("DTO")
                .because("N6 — every wire type is generated from the contract, and the generator suffixes what it emits")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_the_dto_package_when_the_class_is_a_dto() {
        classes()
                .that().haveSimpleNameEndingWith("DTO")
                .should().resideInAPackage("..contract.dto..")
                .because("N6 — a wire type outside the generated package is hand-written, which means it bypassed the contract")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    // N7 — the domain type is the one that needs no qualifier. Pokemon is the Pokemon;
    // PokemonDTO and PokemonDataModel are projections of it. A suffixed name in ..domain..
    // means a projection leaked inward, or that someone could not tell the three apart
    @Test
    void should_carry_no_projection_suffix_when_the_class_is_in_domain() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().haveSimpleNameEndingWith("DTO")
                .orShould().haveSimpleNameEndingWith("Dto")
                .orShould().haveSimpleNameEndingWith("DataModel")
                .orShould().haveSimpleNameEndingWith("Entity")
                .orShould().haveSimpleNameEndingWith("Request")
                .orShould().haveSimpleNameEndingWith("Response")
                .because("N7 — the domain carries the ubiquitous language; a projection suffix there means the projection leaked inward")
                .check(ProjectClasses.production());
    }

    // N9 — a port names a capability, its adapter names the technology. PokemonRepository
    // says what the domain needs; JpaPokemonRepositoryAdapter says how it is met.
    // Records are permitted because a port sometimes needs a carrier for its own return
    // type — CatalogPage(rows, totalCount) exists so one call answers both questions.
    // What is forbidden is a concrete class with behaviour: that is an adapter in hiding.
    @Test
    void should_be_an_interface_or_a_carrier_record_when_the_class_is_in_a_port_package() {
        classes()
                .that().haveSimpleNameEndingWith("Port").and().resideInAPackage("..domain..")
                .should(beAnInterfaceOrARecord())
                .because("N9 — a port declares need; an implementation there is an adapter in the wrong layer")
                .check(ProjectClasses.production());
    }

    @Test
    void should_reside_in_infrastructure_when_the_class_is_an_adapter() {
        classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .should().resideInAnyPackage("..infrastructure..", "..interfaces..")
                .because("N9 — an adapter belongs to an outer ring: persistence and HTTP in interfaces, technology clients in infrastructure")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    @Test
    void should_implement_a_port_when_the_class_is_an_adapter() {
        classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .should(implementAPort())
                .because("N9 — an adapter with no port is not an adapter, it is a class in the infrastructure package")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    private static ArchCondition<JavaClass> beAnInterfaceOrARecord() {
        return new ArchCondition<>("be an interface or a carrier record") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean ok = item.isInterface() || item.isRecord();
                events.add(new SimpleConditionEvent(
                        item,
                        ok,
                        item.getName() + " is a concrete class in a port package"));
            }
        };
    }

    private static ArchCondition<JavaClass> implementAPort() {
        return new ArchCondition<>("implement a port interface declared in a domain package") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean implementsPort = item.getAllRawInterfaces().stream()
                        .anyMatch(i -> i.getPackageName().startsWith(ProjectClasses.ROOT)
                                && i.getPackageName().endsWith(".domain"));
                events.add(new SimpleConditionEvent(
                        item,
                        implementsPort,
                        item.getName() + " implements no port interface from a domain package"));
            }
        };
    }
}

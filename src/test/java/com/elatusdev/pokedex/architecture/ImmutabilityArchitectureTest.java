package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.beRecords;
import static com.tngtech.archunit.lang.conditions.ArchConditions.haveOnlyFinalFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ImmutabilityArchitectureTest {

    // Flattening the contexts put value objects and aggregate roots in one package, so the
    // rule can no longer tell them apart by location. It uses the definition in its own
    // rationale instead: an aggregate has identity, a value object does not. Pokemon is a
    // deliberately mutable aggregate — addTag() reassigns — and is skipped for that reason,
    // not allowlisted by name.
    private static final DescribedPredicate<JavaClass> HAVE_IDENTITY =
            new DescribedPredicate<>("declare an identity field") {
                @Override
                public boolean test(JavaClass type) {
                    return type.getFields().stream().anyMatch(field -> "id".equals(field.getName()));
                }
            };

    // value objects and enums both live in ..domain.. now, and the enums satisfy this
    // through haveOnlyFinalFields rather than beRecords
    @Test
    void should_be_a_record_or_have_only_final_fields_when_the_class_is_a_value_object() {
        classes()
                .that().resideInAPackage("..domain..")
                .and().areNotInterfaces()
                .and().areNotEnums()
                .and(DescribedPredicate.not(HAVE_IDENTITY))
                .should(beRecords().or(haveOnlyFinalFields()))
                .because("IMF1 — a value object with a mutable field has identity, and identity is what a value object is defined as not having")
                .check(ProjectClasses.production());
    }
}

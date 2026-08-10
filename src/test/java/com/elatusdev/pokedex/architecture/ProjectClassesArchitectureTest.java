package com.elatusdev.pokedex.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectClassesArchitectureTest {

    @Test
    void should_import_compiled_production_classes_when_scanning_target_classes() {
        JavaClasses imported = ProjectClasses.production();

        assertThat(imported.stream().map(JavaClass::getName))
                .contains("com.elatusdev.pokedex.pokedex.domain.ReplicationState")
                .contains("com.elatusdev.pokedex.PokedexApplication");
    }

    // The suite asserts on compiled output, so its input is whatever is in target/classes —
    // not what the source says. A renamed or deleted class leaves its old .class behind and
    // keeps failing rules that the source no longer violates: the contract.dto rename left 30
    // web/dto files and produced 28 phantom N6 violations on a tree that was correct.
    //
    // Phantom violations are worse than the staleness. A gate that fails for reasons the code
    // cannot explain is one people learn to re-run rather than read, and then it stops being a
    // gate. This names the cause instead.
    @Test
    void should_import_no_class_whose_source_no_longer_exists() {
        List<String> orphaned = ProjectClasses.production().stream()
                .map(JavaClass::getName)
                .map(ProjectClassesArchitectureTest::topLevelOf)
                .distinct()
                .filter(name -> !Files.exists(sourceFileOf(name)))
                .sorted()
                .toList();

        assertThat(orphaned)
                .as(
                        "stale class files in target/classes — these have no source file. "
                                + "ArchUnit reads compiled output, so they keep failing rules the source "
                                + "no longer violates. Run: mvn -B clean verify")
                .isEmpty();
    }

    private static String topLevelOf(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }

    private static Path sourceFileOf(String className) {
        return Path.of("src/main/java/" + className.replace('.', '/') + ".java");
    }

    @Test
    void should_import_only_the_project_root_package_when_scanning_target_classes() {
        assertThat(ProjectClasses.production().stream().map(JavaClass::getPackageName))
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name).startsWith(ProjectClasses.ROOT));
    }

    @Test
    void should_exclude_test_classes_when_scanning_target_classes() {
        assertThat(ProjectClasses.production().stream().map(JavaClass::getName))
                .doesNotContain(ProjectClassesArchitectureTest.class.getName())
                .noneSatisfy(name -> assertThat(name).endsWith("Test"));
    }

    @Test
    void should_exclude_generated_sources_when_scanning_target_classes() {
        assertThat(ProjectClasses.production().stream().map(JavaClass::getPackageName))
                .noneSatisfy(name -> assertThat(name).startsWith(ProjectClasses.GENERATED_API_PACKAGE))
                .noneSatisfy(name -> assertThat(name).startsWith(ProjectClasses.GENERATED_DTO_PACKAGE));
    }

    @Test
    void should_return_the_same_cached_instance_when_called_repeatedly() {
        assertThat(ProjectClasses.production()).isSameAs(ProjectClasses.production());
    }
}

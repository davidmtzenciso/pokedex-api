package com.elatusdev.pokedex.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectClassesArchitectureTest {

    @Test
    void should_import_compiled_production_classes_when_scanning_target_classes() {
        JavaClasses imported = ProjectClasses.production();

        assertThat(imported.stream().map(JavaClass::getName))
                .contains("com.elatusdev.pokedex.pokedex.domain.model.ReplicationState")
                .contains("com.elatusdev.pokedex.PokedexApplication");
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

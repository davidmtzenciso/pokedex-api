package com.elatusdev.pokedex.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;

public final class ProjectClasses {

    public static final String ROOT = "com.elatusdev.pokedex";
    public static final String GENERATED_API_PACKAGE = ROOT + ".web.api";
    public static final String GENERATED_DTO_PACKAGE = ROOT + ".web.dto";

    private static final String GENERATED_API_PATH = "/com/elatusdev/pokedex/web/api/";
    private static final String GENERATED_DTO_PATH = "/com/elatusdev/pokedex/web/dto/";

    // one import for sixteen rules — ClassFileImporter walks the tree on every call otherwise
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .withImportOption(new ImportOption.DoNotIncludeJars())
            // the generator writes into target/classes alongside our code, so it cannot be
            // excluded by source root — only by the package it is configured to emit into
            .withImportOption(location -> !location.contains(GENERATED_API_PATH))
            .withImportOption(location -> !location.contains(GENERATED_DTO_PATH))
            .importPath(Path.of("target", "classes", "com", "elatusdev", "pokedex"));

    private ProjectClasses() {
    }

    public static JavaClasses production() {
        return PRODUCTION;
    }
}

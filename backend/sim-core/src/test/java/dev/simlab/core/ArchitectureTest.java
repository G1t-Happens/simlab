package dev.simlab.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.simlab.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // besides the JDK the toolkit only uses JSpecify's annotations, which mark its null safety
    @ArchTest
    static final ArchRule toolkitDependsOnTheJdkAlone = classes()
            .that()
            .resideInAPackage("dev.simlab.core.toolkit..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("dev.simlab.core.toolkit..", "java..", "org.jspecify..")
            .because("every simulation may build on the toolkit");

    @ArchTest
    static final ArchRule contractAndScenesKnowNeitherToolkitNorSimulations = noClasses()
            .that()
            .resideInAnyPackage("dev.simlab.core", "dev.simlab.core.scene")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.simlab.core.toolkit..", "dev.simlab.core.simulations..")
            .because("they describe every simulation alike to the server and the browser");

    @ArchTest
    static final ArchRule simulationsDoNotDependOnEachOther = slices().matching("dev.simlab.core.simulations.(*)..")
            .should()
            .notDependOnEachOther()
            .because("each simulation can be read, changed and removed on its own");
}

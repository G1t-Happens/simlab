package dev.simlab.server;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "dev.simlab.server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // the WebSocket endpoint of the rooms takes messages like the REST controllers take requests
    @ArchTest
    static final ArchRule controllersReachRepositoriesThroughServicesAlone = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controllers")
            .definedBy(resideInAPackage("dev.simlab.server.websocket..").or(annotatedWith(RestController.class)))
            .layer("Services")
            .definedBy(annotatedWith(Service.class))
            .layer("Repositories")
            .definedBy(annotatedWith(Repository.class))
            .whereLayer("Controllers")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Services")
            .mayOnlyBeAccessedByLayers("Controllers")
            .whereLayer("Repositories")
            .mayOnlyBeAccessedByLayers("Services")
            .because("controllers turn requests into the use cases of services, and only these reach what"
                    + " repositories keep");

    @ArchTest
    static final ArchRule servicesAndRepositoriesKnowNothingOfHttp = noClasses()
            .that()
            .areAnnotatedWith(Service.class)
            .or()
            .areAnnotatedWith(Repository.class)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.http..", "org.springframework.web..", "jakarta.servlet..")
            .because(
                    "controllers turn their results and exceptions into responses, of HTTP and of the WebSocket alike");
}

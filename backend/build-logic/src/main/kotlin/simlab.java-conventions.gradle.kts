import com.diffplug.gradle.spotless.SpotlessTask
import net.ltgt.gradle.errorprone.errorprone

// conventions for every Java module: toolchain, strict compilation, null safety, formatting, tests and coverage
plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    errorprone(libs.findLibrary("errorprone-core").get())
    errorprone(libs.findLibrary("nullaway").get())
}

tasks.withType<JavaCompile>().configureEach {
    // "processing" would warn about every annotation that no annotation processor claims
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // packages annotated with JSpecify's @NullMarked get checked for null safety at compile time
        error("NullAway")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:HandleTestAssertionLibraries", "true")
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        // comments and tags that would only state the obvious are left out (Google Java Style permits that),
        // so doclint checks everything but missing ones and fails on its warnings
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgumentProviders +=
        MockitoAgentArgumentProvider(classpath.filter { it.name.startsWith("mockito-core-") })
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
    }
}

spotless {
    java {
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        formatAnnotations()
    }
}

// cleaning any project closes the class loaders Spotless keeps for its formatters, even while another project
// formats with them in a parallel build, which then fails at random (diffplug/spotless#3067). So formatting
// waits until every project is clean
tasks.withType<SpotlessTask>().configureEach {
    mustRunAfter(rootProject.subprojects.map { "${it.path}:clean" })
}

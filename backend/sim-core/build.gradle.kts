plugins {
    `java-library`
    id("simlab.java-conventions")
}

description = "Framework-free simulation engine: simulations, the toolkit they are built from and their scenes"

dependencies {
    api(libs.jspecify)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit6)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// the lessons show the code of the simulations, which the server reads from the jar
tasks.processResources {
    into("sources") {
        from(sourceSets.main.map { it.java })
    }
}

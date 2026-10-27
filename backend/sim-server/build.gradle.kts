plugins {
    id("simlab.java-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.gradle.git.properties)
}

description = "Spring Boot server hosting live physics simulations"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":sim-core"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.javaparser.core)
    implementation(libs.snakeyaml)
    compileOnly(libs.errorprone.annotations)

    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.validation.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.websocket.test)
    testImplementation(libs.archunit.junit6)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// the commit links the code in the lessons to its lines on GitHub, built without Git they link to nothing
gitProperties {
    keys = listOf("git.commit.id")
    failOnNoGitDirectory = false
}

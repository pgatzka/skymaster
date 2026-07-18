import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("java-module")
    id("java-library")
    alias(libs.plugins.openapi)
}

val openApiGenerate = tasks.named<GenerateTask>("openApiGenerate")

sourceSets {
    main {
        java {
            srcDir(openApiGenerate.map { "${it.outputDir.get()}/src/main/java" })
        }
    }
}

val openApiSpec = configurations.create("openApiSpec") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    openApiSpec(project(mapOf("path" to ":skymaster-server", "configuration" to "openApiSpec")))
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    implementation(libs.jakarta.annotations)
}

tasks.openApiValidate {
    inputSpec.set(openApiSpec.incoming.files.singleFile.absolutePath)
    dependsOn(openApiSpec)
}

tasks.openApiGenerate {
    inputSpec.set(openApiSpec.incoming.files.singleFile.absolutePath)
    dependsOn(openApiSpec)
    generatorName.set("java")

    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    library.set("native")

    configOptions.set(
        mapOf(
            "useJakartaEe" to "true",
            "openApiNullable" to "false"
        )
    )
}

tasks.compileJava {
    dependsOn(openApiGenerate)
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka:skymaster-api")
    }
}

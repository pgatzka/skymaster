import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("java-module")
    id("maven-publish")
    alias(libs.plugins.loom)
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

java {
    withSourcesJar()
}

repositories {
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create("skymaster") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

val shipped = configurations.create("shipped")

configurations {
    implementation.get().extendsFrom(shipped)
}

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    shipped(platform(libs.jackson.bom))

    shipped(libs.jackson.core)
    shipped(libs.jackson.annotations)
    shipped(libs.jackson.databind)
    shipped(libs.jackson.jsr310)
    shipped(libs.jakarta.annotations)

    runtimeOnly(libs.httpclient)

    localRuntime(libs.devauth)

    openApiSpec(project(mapOf("path" to ":skymaster-server", "configuration" to "openApiSpec")))
}

afterEvaluate {
    shipped.resolvedConfiguration.resolvedArtifacts.forEach { art ->
        dependencies.add("include", art.moduleVersion.id.toString())
    }
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            // Loom wires remapJar into the java component, so this publishes the
            // remapped (production) jar rather than the dev-mappings one.
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/pgatzka/skymaster")
            credentials {
                // GITHUB_ACTOR is injected on every runner. Avoid the name USERNAME
                // here - it collides with the native Windows variable for local runs.
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
    jar {
        inputs.property("name", project.name)
        from("LICENSE") {
            rename { "${it}_${project.name}" }
        }
    }
    openApiValidate.configure {
        inputSpec.set(layout.file(providers.provider { openApiSpec.incoming.files.singleFile }))
        dependsOn(openApiSpec)
    }
    openApiGenerate.configure {
        inputSpec.set(layout.file(providers.provider { openApiSpec.incoming.files.singleFile }))
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
    compileJava {
        dependsOn(openApiGenerate)
    }
}

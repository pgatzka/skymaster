plugins {
    id("java-module")
    id("maven-publish")
    alias(libs.plugins.loom)
}

java {
    withSourcesJar()
}

repositories {
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.notenoughupdates.org/releases/")
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

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    // Both are required and do different jobs: `implementation` puts the API on the
    // compile classpath, `include` nests it in the mod jar (Fabric jar-in-jar) so it
    // resolves at runtime. `include` alone fails to compile; `implementation` alone
    // produces a mod that throws NoClassDefFoundError in game.
    implementation(project(":skymaster-api"))
    include(project(":skymaster-api"))

    runtimeOnly(libs.httpclient)
    localRuntime(libs.devauth)

    implementation(libs.fabric.kotlin)
    implementation(libs.moulconfig)
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
}

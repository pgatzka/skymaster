plugins {
    alias(libs.plugins.loom)
}

repositories {
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("skymaster") {
            sourceSet(sourceSets["main"])
        }
    }

}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    runtimeOnly(libs.httpclient)
    runtimeOnly(libs.devauth)
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.jar {
    inputs.property("name", project.name)
    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}



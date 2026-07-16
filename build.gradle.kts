plugins {
    alias(libs.plugins.loom)
}

repositories {

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



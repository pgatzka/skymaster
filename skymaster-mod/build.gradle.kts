plugins {
    id("java-module")
    alias(libs.plugins.loom)
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

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(project(":skymaster-api"))

    runtimeOnly(libs.httpclient)
    runtimeOnly(libs.devauth)
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    inputs.property("name", project.name)
    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}



plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.plugins.spotless.map {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    })
}
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.plugins.com.diffplug.gradle.spotless.map {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    })
}
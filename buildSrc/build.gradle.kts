plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:8.8.0")
    implementation("org.sonarqube:org.sonarqube.gradle.plugin:7.3.1.8318")
}
plugins {
    id("java-module")
    alias(libs.plugins.lombok)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependencies)
}

dependencies {
    implementation(libs.spring.boot.webmvc)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.webmvc.test)

    testRuntimeOnly(libs.junit.launcher)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}
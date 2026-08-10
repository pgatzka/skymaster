plugins {
    id("formatting-conventions")
    alias(libs.plugins.org.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka:skymaster")
        property("sonar.organization", "pgatzka")
    }
}

allprojects {
    repositories {
        mavenCentral()
    }
}

tasks {
    register<JavaExec>("printVersion") {
        group = "help"
        description = "Prints the project version to quiet"
        doLast {
            logger.quiet(version.toString())
        }
    }
}
plugins {
    alias(libs.plugins.spotless) apply false
}

allprojects {

    repositories {
        mavenCentral()
    }

}

tasks{
    register<Exec>("installGitHooks") {
        description = "Install git hooks"
        commandLine = listOf("git", "config", "core.hooksPath", ".githooks")
    }
    register("printVersion") {
        group = "help"
        description = "Prints the project version to quiet"
        doLast {
            logger.quiet(version.toString())
        }
    }
}
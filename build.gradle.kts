allprojects {

    repositories {
        mavenCentral()
    }

}

tasks.register<Exec>("installGitHooks") {
    description = "Install git hooks"
    commandLine = listOf("git", "config", "core.hooksPath", ".githooks")
}
plugins {
    id("java")
    id("org.sonarqube")
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
}

spotless {
    java {
        palantirJavaFormat()
    }
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka:skymaster")
        property("sonar.organization", "pgatzka")
    }
}
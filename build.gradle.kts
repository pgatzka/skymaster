plugins {
    id("com.diffplug.spotless") version "8.9.0"
}

spotless {
    java {
        target("**/*.java")
        eclipse("4.40").configFile("config/eclipse-formatter.xml")
        forbidWildcardImports()
        removeUnusedImports()
    }
    kotlinGradle {
        ktlint()
    }
}

repositories {
    mavenCentral()
}

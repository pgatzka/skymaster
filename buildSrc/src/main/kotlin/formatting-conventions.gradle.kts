plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**")
        palantirJavaFormat()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }
}
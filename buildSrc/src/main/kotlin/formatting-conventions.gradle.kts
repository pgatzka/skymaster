plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        target("src/**/*.java")
        targetExclude("**/build/**")
        palantirJavaFormat()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
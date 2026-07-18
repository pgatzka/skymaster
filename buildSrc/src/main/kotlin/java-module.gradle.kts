plugins {
    id("java")
    id("jacoco")
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
        // Target the checked-in sources directly rather than letting Spotless derive
        // them from the source sets. :skymaster-api's main source set includes the
        // OpenAPI-generated directory, so a source-set-derived target makes
        // spotlessCheck depend on codegen - which boots the Spring app to produce the
        // spec. That turned a formatting check into a 2.5 minute job.
        target("src/**/*.java")
        palantirJavaFormat()
    }
}

// Sonar does not measure coverage, it only imports it. Without a JaCoCo XML
// report produced by this module's own test run, the project reports 0%.
val jacocoXmlReport = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = false
    }
}

// Keeps coverage a product of the normal build rather than a task CI must
// remember to add. `check` is what `build` runs.
tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka:${project.name}")
        property("sonar.organization", "pgatzka")
        property("sonar.coverage.jacoco.xmlReportPaths", jacocoXmlReport.get().asFile.path)
    }
}
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

spotless {
    java {
        // Target the checked-in sources directly rather than letting Spotless derive
        // them from the source sets. :skymaster-mod's main source set includes the
        // OpenAPI-generated directory, so a source-set-derived target makes
        // spotlessCheck depend on codegen - which boots the Spring app to produce the
        // spec. That turned a formatting check into a 2.5 minute job.
        target("src/**/*.java")
        palantirJavaFormat()
    }
}

val jacocoXmlReport = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")

tasks {
    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required = true
            html.required = true
        }
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/generated/**")
            }
        }))
    }
    jacocoTestCoverageVerification {
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/generated/**")
            }
        }))
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal.valueOf(0.8)
                }
            }
            rule {
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "0.70".toBigDecimal()
                }
            }
            rule {
                element = "CLASS"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.50".toBigDecimal()
                }
            }
        }
    }
    test {
        finalizedBy(jacocoTestReport)
        testLogging {
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
        useJUnitPlatform()
    }
    compileJava {
        options.release.set(25)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka:${project.name}")
        property("sonar.organization", "pgatzka")
        property("sonar.coverage.jacoco.xmlReportPaths", jacocoXmlReport.get().asFile.path)
    }
}
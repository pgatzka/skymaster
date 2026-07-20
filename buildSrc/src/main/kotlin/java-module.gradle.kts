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
    }
    jacocoTestCoverageVerification {
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
                excludes = listOf(

                )
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
    withType<JavaCompile> {
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
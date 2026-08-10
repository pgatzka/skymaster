plugins {
    id("java")
    id("jacoco")
    id("formatting-conventions")
    alias(libs.plugins.io.freefair.lombok)
    alias(libs.plugins.io.spring.dependency.management)
    alias(libs.plugins.org.springframework.boot)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    developmentOnly(libs.bundles.skymaster.server.development.only)
    implementation(libs.bundles.skymaster.server.implementation)
    runtimeOnly(libs.bundles.skymaster.server.runtime.only)
    testImplementation(libs.bundles.skymaster.server.test.implementation)
    testRuntimeOnly(libs.bundles.skymaster.server.test.runtime.only)
    mockitoAgent(libs.bundles.skymaster.server.mockito.agent) { isTransitive = false }
}

val coverageExclusions =
    listOf(
        "**/*Application*", // Spring Boot entrypoint
    )

fun filteredClasses(): FileCollection =
    files(
        sourceSets.main.get().output.classesDirs.map { dir ->
            fileTree(dir) { exclude(coverageExclusions) }
        },
    )

tasks {
    test {
        finalizedBy(jacocoTestReport)
        useJUnitPlatform()
        jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    jacocoTestReport {
        reports {
            xml.required.set(true)
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
    }
    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        classDirectories.setFrom(filteredClasses())
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
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
}

springBoot {
    buildInfo()
}

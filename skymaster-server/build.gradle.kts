import kotlin.random.Random
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("java")
    id("jacoco")
    alias(libs.plugins.sonar)
    alias(libs.plugins.spotless)
    alias(libs.plugins.lombok)
    alias(libs.plugins.springdoc)
    alias(libs.plugins.openapi)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependencies)
}

val openApiGenerate = tasks.named<GenerateTask>("openApiGenerate")

sourceSets {
    main {
        java {
            srcDir(openApiGenerate.map { "${it.outputDir.get()}/src/main/java" })
        }
    }
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

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.webmvc)
    implementation(libs.springdoc)

    // The generated Hypixel client uses Jackson 2 (com.fasterxml), which does not
    // come with Spring Boot 4's Jackson 3 (tools.jackson) starters.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    implementation(libs.jakarta.annotations)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.actuator.test)
    testImplementation(libs.spring.boot.webmvc.test)

    testRuntimeOnly(libs.junit.launcher)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

springBoot {
    buildInfo()
}

val jacocoXmlReport = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")

tasks {
    jar {
        enabled = false
    }
    test {
        jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    openApiValidate {
        inputSpec.set(layout.projectDirectory.file("src/main/openapi/hypixel.json").asFile.path)
    }
    openApiGenerate {
        inputSpec.set(layout.projectDirectory.file("src/main/openapi/hypixel.json").asFile.path)
        generatorName.set("java")

        generateApiTests.set(false)
        generateModelTests.set(false)
        generateApiDocumentation.set(false)
        generateModelDocumentation.set(false)
        library.set("restclient")

        invokerPackage.set("io.github.pgatzka.skymaster.generated.hypixel")
        apiPackage.set("io.github.pgatzka.skymaster.generated.hypixel.api")
        modelPackage.set("io.github.pgatzka.skymaster.generated.hypixel.model")

        configOptions.set(
            mapOf(
                "useJakartaEe" to "true",
                "openApiNullable" to "false"
            )
        )
    }
    compileJava {
        dependsOn(openApiGenerate)
        options.release.set(25)
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
    }
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
}

val openApiGeneratePort = Random.nextInt(8080, 9090)

openApi {
    outputDir.set(layout.buildDirectory.dir("openApi"))
    outputFileName.set("spec.json")
    apiDocsUrl.set(apiDocsUrl.get().replace("8080", "$openApiGeneratePort"))
    customBootRun {
        args.set(
            listOf(
                "--server.port=$openApiGeneratePort",
                "--springdoc.api-docs.enabled=true",
                "--springdoc.swagger-ui.enabled=true",
                "--logging.level.root=warn",
                // Spec generation boots the application, which requires the Hypixel
                // API key property. That boot never calls Hypixel, so any value works.
                "--skymaster.hypixel.api-key=dummy-spec-generation-key"
            )
        )
    }
}

val openApiSpec = configurations.create("openApiSpec") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("openApiSpec", layout.buildDirectory.file("openApi/spec.json")) {
        builtBy(tasks.generateOpenApiDocs)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka:skymaster-server")
        property("sonar.organization", "pgatzka")
        property("sonar.coverage.jacoco.xmlReportPaths", jacocoXmlReport.get().asFile.path)
    }
}
import kotlin.random.Random

plugins {
    id("java-module")
    alias(libs.plugins.lombok)
    alias(libs.plugins.springdoc)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.boot.aot)
    alias(libs.plugins.spring.dependencies)
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.webmvc)
    implementation(libs.springdoc)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.actuator.test)
    testImplementation(libs.spring.boot.webmvc.test)

    testRuntimeOnly(libs.junit.launcher)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

springBoot {
    buildInfo()
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    forkedSpringBootRun {
        dependsOn(compileAotJava, processAotResources, processAot)
    }
    jar {
        enabled = false
    }
    test {
        jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    }
    processTestAot {
        jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
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
                "--logging.level.root=warn"
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

import kotlin.random.Random

plugins {
    id("java-module")
    alias(libs.plugins.lombok)
    alias(libs.plugins.springdoc)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.boot.aot)
    alias(libs.plugins.spring.dependencies)
}

dependencies {
    implementation(libs.spring.boot.webmvc)
    implementation(libs.springdoc)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.webmvc.test)

    testRuntimeOnly(libs.junit.launcher)
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
    // Without this, build/libs holds both the boot jar and a `-plain` jar, and the
    // Dockerfile's `COPY build/libs/*.jar application.jar` fails: Docker requires a
    // directory destination when the source glob matches more than one file.
    // Nothing consumes this module as a library, so the plain jar has no use.
    jar {
        enabled = false
    }
}

val openApiGeneratePort = Random.nextInt(8080, 9090)

openApi {
    outputDir.set(layout.buildDirectory.dir("openApi"))
    outputFileName.set("spec.json")
    apiDocsUrl.set(apiDocsUrl.get().replace("8080", "$openApiGeneratePort"))
    customBootRun {
        args.set(listOf(
            "--spring.docker.compose.file=${file("compose.yaml").absolutePath}",
            "--server.port=$openApiGeneratePort",
            "--springdoc.api-docs.enabled=true",
            "--springdoc.swagger-ui.enabled=true"
        ))
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
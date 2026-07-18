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

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    forkedSpringBootRun {
        dependsOn(compileAotJava, processAotResources, processAot)
    }
}

openApi {
    outputDir.set(layout.buildDirectory.dir("openApi"))
    outputFileName.set("spec.json")
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
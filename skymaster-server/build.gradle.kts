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
}

springBoot {
    buildInfo()
}
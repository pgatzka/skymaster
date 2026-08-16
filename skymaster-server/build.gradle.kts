plugins {
    id("java")
    alias(libs.plugins.io.freefair.lombok)
    alias(libs.plugins.org.springframework.boot)
    alias(libs.plugins.io.spring.dependency.management)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.server.implementation)
    testImplementation(libs.bundles.server.test.implementation)
    testRuntimeOnly(libs.bundles.server.test.runtime.only)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        jvmArgs("-Xshare:off")
    }
}

springBoot {
    buildInfo()
}
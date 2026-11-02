// SPDX-License-Identifier: MPL-2.0

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-library`
}

group = "io.github.bric3.glowadoc"

dependencyLocking {
    lockAllConfigurations()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(platform("org.junit:junit-bom:${libraries.findVersion("junit").get().requiredVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libraries.findLibrary("tabletest").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "1g"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

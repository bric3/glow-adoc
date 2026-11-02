// SPDX-License-Identifier: MPL-2.0

plugins {
    `kotlin-dsl`
    id("glow.quality")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("com.javiersc.semver:com.javiersc.semver.gradle.plugin:0.9.0")
    testImplementation(platform("org.junit:junit-bom:${libs.versions.junit.get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

dependencyLocking {
    lockAllConfigurations()
}

// SPDX-License-Identifier: MPL-2.0

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.javiersc.semver:com.javiersc.semver.gradle.plugin:0.9.0")
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
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

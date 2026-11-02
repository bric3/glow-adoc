// SPDX-License-Identifier: MPL-2.0

plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.spotless.gradle)
    implementation(libs.yumi.licenser.gradle)
    testImplementation(platform("org.junit:junit-bom:${libs.versions.junit.get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty(
        "quality.catalog",
        layout.projectDirectory.file("../../gradle/libs.versions.toml").asFile.absolutePath,
    )
}

dependencyLocking {
    lockAllConfigurations()
}

// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":glowadoc-cache"))
    api(project(":glowadoc-highlight"))
    implementation(libs.lumis)
}

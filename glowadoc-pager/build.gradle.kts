// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":glowadoc-render"))
    implementation(project(":glowadoc-terminal"))
    implementation(project(":glowadoc-highlight"))
    implementation(project(":glowadoc-diagrams"))
    implementation(libs.tamboui.tui)
}

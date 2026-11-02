// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":glowadoc-render"))
    implementation(project(":glowadoc-terminal"))
    implementation(project(":glowadoc-highlight-common"))
    implementation(project(":glowadoc-diagrams-common"))
    implementation(libs.tamboui.tui)
}

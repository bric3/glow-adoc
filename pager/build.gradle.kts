// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":render"))
    implementation(project(":terminal"))
    implementation(project(":highlight"))
    implementation(project(":diagrams"))
    implementation(libs.tamboui.tui)
}

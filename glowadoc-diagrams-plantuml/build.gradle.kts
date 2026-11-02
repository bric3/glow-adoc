// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glowadoc.java")
}

dependencies {
    api(project(":glowadoc-diagrams-common"))
    implementation(libs.plantuml)
}

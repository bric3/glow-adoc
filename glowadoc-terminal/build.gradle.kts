// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    api(project(":glowadoc-document"))
    api(libs.tamboui.aesh)
}

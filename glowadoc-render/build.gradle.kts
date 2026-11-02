// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    api(project(":glowadoc-document"))
    api(project(":glowadoc-highlight-common"))
    implementation("dev.tamboui:tamboui-core:0.6.0")
}

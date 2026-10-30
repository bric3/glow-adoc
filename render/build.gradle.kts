// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    api(project(":document"))
    api(project(":highlight"))
    implementation("dev.tamboui:tamboui-core:0.6.0")
}

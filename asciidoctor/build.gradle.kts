// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    api(project(":document"))
    implementation(libs.asciidoctor)
}

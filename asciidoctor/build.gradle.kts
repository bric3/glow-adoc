// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":cache"))
    api(project(":document"))
    implementation(libs.asciidoctor)
    implementation(libs.jsoup)
}

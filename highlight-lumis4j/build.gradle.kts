// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    api(project(":highlight"))
    implementation(libs.lumis)
}

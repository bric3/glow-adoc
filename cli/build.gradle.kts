// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":asciidoctor"))
    implementation(project(":render"))
    implementation(project(":highlight-lumis4j"))
    implementation(project(":terminal"))
    implementation(project(":pager"))
    implementation(project(":diagrams"))
    implementation(libs.aesh)
    annotationProcessor(libs.aesh.processor)
    implementation(libs.tomlj)
}

// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
}

dependencies {
    implementation(project(":glowadoc-asciidoctor"))
    implementation(project(":glowadoc-render"))
    implementation(project(":glowadoc-highlight-lumis4j"))
    implementation(project(":glowadoc-terminal"))
    implementation(project(":glowadoc-pager"))
    implementation(project(":glowadoc-diagrams"))
    implementation(libs.aesh)
    annotationProcessor(libs.aesh.processor)
    implementation(libs.tomlj)
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("fixtures/aot")) {
        into("META-INF/glow-adoc/aot")
    }
}

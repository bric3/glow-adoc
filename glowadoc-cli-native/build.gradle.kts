// SPDX-License-Identifier: MPL-2.0

import io.github.bric3.glowadoc.buildlogic.NativeImage

plugins {
    id("glowadoc.java")
    application
}

dependencies {
    implementation(project(":glowadoc-cli-common"))
}

application {
    mainClass = "io.github.bric3.glowadoc.cli.Main"
}

val nativeCompile = tasks.register<NativeImage>("nativeCompile") {
    group = "distribution"
    description = "Experimental full-application Native Image feasibility check."
    runtimeClasspath.from(configurations.runtimeClasspath)
    output = layout.buildDirectory.file("native/glow-adoc")
    compiler = providers.environmentVariable("GRAALVM_HOME").map { "$it/bin/native-image" }.orElse("native-image")
    mainClass = application.mainClass
}

// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glowadoc.java")
}

dependencies {
    implementation(project(":glowadoc-cache"))
    api(project(":glowadoc-document"))
    implementation(libs.asciidoctor)
    implementation(libs.jsoup)

    constraints {
        // AsciidoctorJ 3.x declares JRuby 9; keep its runtime components on one tested release.
        implementation(libs.jruby.runtime)
        implementation(libs.jruby.base)
        implementation(libs.jruby.stdlib)
    }
}

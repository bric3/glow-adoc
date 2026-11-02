// SPDX-License-Identifier: MPL-2.0

plugins {
    base
    id("glow.quality")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run checks in all application modules."
    dependsOn(tasks.check)
    dependsOn(gradle.includedBuild("glowadoc-build-logic").task(":check"))
    dependsOn(gradle.includedBuild("glowadoc-quality").task(":test"))
    dependsOn(
        ":glowadoc-cache:test",
        ":glowadoc-cli-jvm:build",
        ":glowadoc-cli:test",
        ":glowadoc-asciidoctor:test",
        ":glowadoc-highlight-lumis4j:test",
        ":glowadoc-highlight:test",
        ":glowadoc-render:test",
        ":glowadoc-terminal:test",
        ":glowadoc-pager:test",
        ":glowadoc-diagrams:test",
    )
}

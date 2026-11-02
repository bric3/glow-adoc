// SPDX-License-Identifier: MPL-2.0

plugins {
    base
    id("glowadoc.quality")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run checks for all application modules and build conventions."
    dependsOn(tasks.check)
    dependsOn(gradle.includedBuild("glowadoc-conventions").task(":check"))
    dependsOn(gradle.includedBuild("glowadoc-settings-conventions").task(":check"))
    dependsOn(
        ":glowadoc-cache:test",
        ":glowadoc-cli-jvm:build",
        ":glowadoc-cli-common:test",
        ":glowadoc-asciidoctor:test",
        ":glowadoc-highlight-lumis4j:test",
        ":glowadoc-highlight-common:test",
        ":glowadoc-render:test",
        ":glowadoc-terminal:test",
        ":glowadoc-pager:test",
        ":glowadoc-diagrams-common:test",
        ":glowadoc-diagrams-plantuml:test",
        ":glowadoc-diagrams-merman:test",
    )
}

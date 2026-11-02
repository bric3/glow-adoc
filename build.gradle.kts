// SPDX-License-Identifier: MPL-2.0

plugins {
    base
    id("glow.quality")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run checks in all application modules."
    dependsOn(tasks.check)
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
    dependsOn(gradle.includedBuild("quality").task(":test"))
    dependsOn(
        ":cache:test",
        ":cli-jvm:build",
        ":cli:test",
        ":asciidoctor:test",
        ":highlight-lumis4j:test",
        ":highlight:test",
        ":render:test",
        ":terminal:test",
        ":pager:test",
        ":diagrams:test",
    )
}

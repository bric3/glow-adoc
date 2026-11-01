// SPDX-License-Identifier: MPL-2.0

plugins {
    base
}

tasks.register("checkAll") {
    group = "verification"
    description = "Run checks in all application modules."
    dependsOn(
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

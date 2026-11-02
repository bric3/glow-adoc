// SPDX-License-Identifier: MPL-2.0

pluginManagement {
    includeBuild("build-logic/quality") {
        name = "quality"
    }
    includeBuild("build-logic")
}

plugins {
    id("glow.versioning")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "glow-adoc"

include(
    "cache",
    "document",
    "asciidoctor",
    "highlight",
    "highlight-lumis4j",
    "render",
    "terminal",
    "pager",
    "diagrams",
    "cli",
    "cli-jvm",
    "cli-native",
)

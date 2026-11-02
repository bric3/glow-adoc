// SPDX-License-Identifier: MPL-2.0

pluginManagement {
    includeBuild("build-logic/quality") {
        name = "glowadoc-quality"
    }
    includeBuild("build-logic") {
        name = "glowadoc-build-logic"
    }
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
    "glowadoc-cache",
    "glowadoc-document",
    "glowadoc-asciidoctor",
    "glowadoc-highlight",
    "glowadoc-highlight-lumis4j",
    "glowadoc-render",
    "glowadoc-terminal",
    "glowadoc-pager",
    "glowadoc-diagrams",
    "glowadoc-cli",
    "glowadoc-cli-jvm",
    "glowadoc-cli-native",
)

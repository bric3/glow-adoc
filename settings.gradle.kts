// SPDX-License-Identifier: MPL-2.0

pluginManagement {
    includeBuild("build-logic/conventions") {
        name = "glowadoc-conventions"
    }
    includeBuild("build-logic/settings-conventions") {
        name = "glowadoc-settings-conventions"
    }
}

plugins {
    id("glowadoc.versioning")
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
    "glowadoc-highlight-common",
    "glowadoc-highlight-lumis4j",
    "glowadoc-render",
    "glowadoc-terminal",
    "glowadoc-images",
    "glowadoc-pager",
    "glowadoc-diagrams-common",
    "glowadoc-diagrams-ditaa",
    "glowadoc-diagrams-plantuml",
    "glowadoc-diagrams-merman",
    "glowadoc-cli-common",
    "glowadoc-cli-jvm",
    "glowadoc-cli-native",
)

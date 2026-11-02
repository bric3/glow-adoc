// SPDX-License-Identifier: MPL-2.0

pluginManagement {
    includeBuild("quality") {
        name = "quality"
    }
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
    }
}

rootProject.name = "glow-adoc-build-logic"

// SPDX-License-Identifier: MPL-2.0

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
    }
}

rootProject.name = "glowadoc-settings-conventions"

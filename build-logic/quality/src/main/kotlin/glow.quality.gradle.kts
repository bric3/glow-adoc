// SPDX-License-Identifier: MPL-2.0

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.yumi.gradle.licenser.YumiLicenserGradleExtension
import dev.yumi.gradle.licenser.api.rule.HeaderRule
import io.github.bric3.glowadoc.buildlogic.SpdxLineComment
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
    id("dev.yumi.gradle.licenser")
}

// Keep the same rules in the application build and the independently runnable build-logic build.
val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")
val excluded = listOf("**/build/**", "**/.gradle/**", "build-logic/**", "fixtures/**", "licenses/**")
val ownedSources = fileTree(layout.projectDirectory) {
    include("**/src/**/*.java", "tools/*.java", "**/*.kt", "**/*.gradle.kts", "tools/*.sh", "packaging/**/*.rb")
    exclude(excluded)
}

configure<SpotlessExtension> {
    java {
        target(ownedSources.matching { include("**/*.java") })
        removeUnusedImports()
        princeOfSpace(versions.findVersion("prince-of-space").get().requiredVersion).apply {
            indentSize(4)
            lineLength(120)
            javaLanguageLevel(25)
        }
        tableTestFormatter(versions.findVersion("tabletest-formatter").get().requiredVersion)
    }
    kotlin {
        target(ownedSources.matching { include("**/*.kt") })
        ktlint(versions.findVersion("ktlint").get().requiredVersion)
    }
    kotlinGradle {
        target(ownedSources.matching { include("**/*.gradle.kts") })
        ktlint(versions.findVersion("ktlint").get().requiredVersion)
    }
}

configure<YumiLicenserGradleExtension> {
    projectCreationYear = 2026
    rule(HeaderRule.parse("MPL-2.0", listOf("SPDX-License-Identifier: MPL-2.0")))
    headerCommentManager.register(SpdxLineComment("//"), "java", "kt", "kts")
    headerCommentManager.register(SpdxLineComment("#"), "sh", "rb")
    registerTasks("owned", ownedSources)
}

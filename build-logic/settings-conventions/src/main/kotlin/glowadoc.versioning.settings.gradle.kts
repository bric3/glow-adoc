// SPDX-License-Identifier: MPL-2.0

import com.javiersc.semver.settings.gradle.plugin.SemverSettingsExtension
import io.github.bric3.glowadoc.buildlogic.ReleaseVersionMapper

val releaseTag = providers.gradleProperty("releaseTag").orNull

if (releaseTag != null) {
    val releaseVersion = ReleaseVersionMapper.releaseVersion(
        releaseTag,
        providers.gradleProperty("releasePrerelease").orNull?.takeIf { it.isNotBlank() }?.toBooleanStrict(),
    )
    fun git(vararg arguments: String): String = providers.exec {
        commandLine("git", "-C", rootDir.absolutePath, *arguments)
    }.standardOutput.asText.get().trim()

    require(git("rev-parse", "HEAD") == git("rev-parse", "refs/tags/$releaseTag^{commit}")) {
        "Release builds must check out $releaseTag"
    }
    require(git("status", "--porcelain").isEmpty()) { "Release builds require a clean checkout" }

    // The selected tag supplies the version when several tags point to the same commit.
    gradle.lifecycle.beforeProject {
        version = releaseVersion
    }
} else {
    apply(plugin = "com.javiersc.semver")

    val releaseTags = providers.exec {
        commandLine("git", "tag", "--merged", "HEAD", "--list", "v[0-9]*")
    }.standardOutput.asText.get().lineSequence().filter { it.matches(Regex("v\\d+\\.\\d+\\.\\d+")) }.toList()
    val headTags = providers.exec {
        commandLine("git", "tag", "--points-at", "HEAD", "--list", "v[0-9]*")
    }.standardOutput.asText.get().lines()

    if (releaseTags.isEmpty()) {
        // Semver 0.9.0 rejects snapshots below its untagged 0.1.0 baseline.
        // Bootstrap the first snapshot until a release tag supplies the baseline.
        val initialVersion = when (providers.gradleProperty("semver.scope").orNull) {
            "major" -> "1.0.0-SNAPSHOT"
            "minor" -> "0.2.0-SNAPSHOT"
            "patch" -> "0.1.1-SNAPSHOT"
            else -> "0.1.0-SNAPSHOT"
        }
        gradle.beforeProject {
            version = initialVersion
            afterEvaluate { version = initialVersion }
        }
    } else {
        extensions.configure<SemverSettingsExtension> {
            mapVersion(
                ReleaseVersionMapper(
                    headTags.any { it in releaseTags },
                    providers.gradleProperty("semver.scope").isPresent,
                ),
            )
        }
    }
}

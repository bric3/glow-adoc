// SPDX-License-Identifier: MPL-2.0

import io.github.bric3.glowadoc.buildlogic.ReleaseVersionMapper

plugins {
    id("com.javiersc.semver")
}

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
    semver {
        mapVersion(
            ReleaseVersionMapper(
                headTags.any {
                    it in releaseTags
                },
                providers.gradleProperty("semver.scope").isPresent,
            ),
        )
    }
}

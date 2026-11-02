// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import com.javiersc.gradle.version.GradleVersion
import com.javiersc.semver.project.gradle.plugin.VersionMapper

class ReleaseVersionMapper(private val tagged: Boolean, private val explicitBump: Boolean) : VersionMapper {
    override fun map(version: GradleVersion): String {
        val base = "${version.major}.${version.minor}.${version.patch}"
        if (tagged && !explicitBump && version.metadata.isNullOrBlank()) {
            return base
        }
        return if (explicitBump) "$base-SNAPSHOT" else "${version.major}.${version.minor}.${version.patch + 1}-SNAPSHOT"
    }

    companion object {
        fun releaseVersion(tag: String, prerelease: Boolean?): String {
            require(tag.startsWith("v")) { "Release tags must start with v: $tag" }
            val version = GradleVersion(tag.removePrefix("v"), GradleVersion.CheckMode.Significant)
            require(version.metadata == null && !version.stage?.name.equals("SNAPSHOT", ignoreCase = true)) {
                "Release tags must contain a release or prerelease version: $tag"
            }
            require(prerelease == null || prerelease == (version.stage != null)) {
                "The GitHub prerelease flag must match the release tag: $tag"
            }
            return version.toString()
        }
    }
}

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
}

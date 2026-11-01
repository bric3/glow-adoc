// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermissions

@UntrackedTask(because = "Installation updates a local executable and its AOT-sensitive timestamp")
abstract class InstallExecutable : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:OutputFile
    abstract val executable: RegularFileProperty

    @TaskAction
    fun install() {
        val target = executable.get().asFile.toPath()
        Files.createDirectories(target.parent)
        // COPY_ATTRIBUTES keeps the timestamp recorded by AOT training.
        Files.copy(jar.get().asFile.toPath(), target, REPLACE_EXISTING, COPY_ATTRIBUTES)
        if (target.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"))
        } else {
            check(target.toFile().setExecutable(true, false)) { "Cannot make executable: $target" }
        }
    }
}

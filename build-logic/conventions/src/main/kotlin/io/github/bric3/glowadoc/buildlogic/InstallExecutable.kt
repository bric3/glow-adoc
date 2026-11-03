// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermissions

@UntrackedTask(because = "Installation updates a local launcher and preserves the jar's AOT-sensitive timestamp")
abstract class InstallExecutable : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherScript: RegularFileProperty

    @get:OutputFile
    abstract val executable: RegularFileProperty

    @get:OutputFile
    abstract val installedJar: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val aotCache: DirectoryProperty

    @get:OutputDirectory
    abstract val installedAot: DirectoryProperty

    @TaskAction
    fun install() {
        val target = executable.get().asFile.toPath()
        val archive = installedJar.get().asFile.toPath()
        replace(archive) { staged ->
            Files.copy(jar.get().asFile.toPath(), staged, REPLACE_EXISTING, COPY_ATTRIBUTES)
            if (aotCache.isPresent) {
                Files.setLastModifiedTime(staged, AOT_JAR_TIMESTAMP)
            }
        }
        val cache = installedAot.get().asFile.toPath()
        for (name in listOf("cache.aot", "cache.key")) {
            if (aotCache.isPresent) {
                replace(cache.resolve(name)) { staged ->
                    Files.copy(aotCache.file(name).get().asFile.toPath(), staged, REPLACE_EXISTING)
                }
            } else {
                Files.deleteIfExists(cache.resolve(name))
            }
        }
        val quotedJar = "'${archive.toAbsolutePath().toString().replace("'", "'\"'\"'")}'"
        replace(target) { staged ->
            val launcher = launcherScript.get().asFile.readText(Charsets.UTF_8).replace("@JAR@", quotedJar)
            Files.writeString(staged, launcher)
            if (staged.fileSystem.supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rwxr-xr-x"))
            } else {
                check(staged.toFile().setExecutable(true, false)) { "Cannot make executable: $target" }
            }
        }
    }

    private fun replace(target: Path, write: (Path) -> Unit) {
        Files.createDirectories(target.parent)
        val staged = Files.createTempFile(target.parent, ".glow-adoc-", ".tmp")
        try {
            write(staged)
            try {
                Files.move(staged, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(staged)
        }
    }
}

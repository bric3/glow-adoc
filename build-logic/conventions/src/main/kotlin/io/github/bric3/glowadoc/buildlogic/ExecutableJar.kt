// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Creates standalone and jar-prefixed POSIX launchers for the shaded jar.
 * The JVM locates the ZIP directory from the end of the file, allowing jar entries to follow the script.
 */
@CacheableTask
abstract class ExecutableJar : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherScript: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputLauncher: RegularFileProperty

    @TaskAction
    fun create() {
        val template = launcherScript.get().asFile.readText(Charsets.UTF_8)
        // The build hint filters AOT misses; complete artifact hashes still validate candidates.
        val generation = MessageDigest.getInstance("SHA-256")
        generation.update(template.toByteArray(Charsets.UTF_8))
        DigestInputStream(inputJar.get().asFile.inputStream(), generation).use {
            it.transferTo(OutputStream.nullOutputStream())
        }
        val launcher = template.replace("@CACHE_GENERATION@", HexFormat.of().formatHex(generation.digest()))

        val script = outputLauncher.get().asFile
        script.parentFile.mkdirs()
        script.writeText(launcher.replace("@JAR@", "\"\$launcher_dir/../lib/glow-adoc.jar\""), Charsets.UTF_8)
        check(script.setExecutable(true, false)) { "Cannot make executable: $script" }

        val target = outputJar.get().asFile
        target.parentFile.mkdirs()
        target.outputStream().use { output ->
            output.write((launcher.replace("@JAR@", "\"\$0\"").trimEnd() + "\n\n").toByteArray(Charsets.UTF_8))
            inputJar.get().asFile.inputStream().use { it.copyTo(output) }
        }
        check(target.setExecutable(true, false)) { "Cannot make executable: $target" }
    }
}

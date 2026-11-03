// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.time.Instant
import javax.inject.Inject

val AOT_JAR_TIMESTAMP: FileTime = FileTime.from(Instant.parse("1980-02-01T00:00:00Z"))

@CacheableTask
abstract class AotTrain @Inject constructor(private val execution: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:Input
    abstract val runtimeIdentity: Property<String>

    @get:OutputDirectory
    abstract val cacheDirectory: DirectoryProperty

    @TaskAction
    fun train() {
        val staged = temporaryDir.toPath().resolve("glow-adoc.jar")
        Files.copy(jar.get().asFile.toPath(), staged, REPLACE_EXISTING, COPY_ATTRIBUTES)
        // HotSpot validates this timestamp; installation and ZIP extraction preserve the same UTC value.
        Files.setLastModifiedTime(staged, AOT_JAR_TIMESTAMP)
        val trainingCache = temporaryDir.resolve("cache")
        check(trainingCache.deleteRecursively()) { "Cannot clear training cache: $trainingCache" }
        execution.exec {
            environment("XDG_CACHE_HOME", trainingCache.absolutePath)
            commandLine(staged.toString(), "--train-aot")
        }
        val trained = trainingCache.resolve("glow-adoc/aot").listFiles { file -> file.extension == "aot" }!!.single()
        val output = cacheDirectory.get().asFile
        check(output.deleteRecursively()) { "Cannot clear packaged cache: $output" }
        output.mkdirs()
        Files.copy(trained.toPath(), output.resolve("cache.aot").toPath(), REPLACE_EXISTING)
        output.resolve("cache.key").writeText("${trained.nameWithoutExtension}\n", Charsets.UTF_8)
    }
}

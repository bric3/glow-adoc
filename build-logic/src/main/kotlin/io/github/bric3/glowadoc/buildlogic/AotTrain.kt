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
import org.gradle.process.ExecOperations
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject

@UntrackedTask(because = "Training updates an opt-in cache for the launcher-selected local JDK")
abstract class AotTrain @Inject constructor(private val execution: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:OutputFile
    abstract val executable: RegularFileProperty

    @TaskAction
    fun train() {
        val staged = executable.get().asFile.toPath()
        Files.createDirectories(staged.parent)
        // HotSpot permits relocation with the same basename and jar timestamp.
        Files.copy(jar.get().asFile.toPath(), staged, REPLACE_EXISTING, COPY_ATTRIBUTES)
        execution.exec {
            commandLine(staged.toString(), "--train-aot")
        }
    }
}

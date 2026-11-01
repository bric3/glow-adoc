// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import javax.inject.Inject

@UntrackedTask(because = "Training updates an opt-in cache for the launcher-selected local JDK")
abstract class AotTrain @Inject constructor(private val execution: ExecOperations) : DefaultTask() {
    @get:InputFile
    abstract val jar: RegularFileProperty

    @get:InputFile
    abstract val document: RegularFileProperty

    @TaskAction
    fun train() {
        execution.exec {
            commandLine(jar.get().asFile.absolutePath, "--train-aot", document.get().asFile.absolutePath)
        }
    }
}

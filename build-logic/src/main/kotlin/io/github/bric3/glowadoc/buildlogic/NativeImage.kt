// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Native executables depend on the local compiler and platform")
abstract class NativeImage @Inject constructor(private val execution: ExecOperations) : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val compiler: Property<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun compile() {
        output.get().asFile.parentFile.mkdirs()
        execution.exec {
            commandLine(
                compiler.get(),
                "--no-fallback",
                "-cp",
                runtimeClasspath.asPath,
                mainClass.get(),
                "-o",
                output.get().asFile.absolutePath,
            )
        }
    }
}

// SPDX-License-Identifier: MPL-2.0

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "AOT caches depend on the exact local JVM and platform")
abstract class AotTrain @Inject constructor(private val execution: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val document: RegularFileProperty

    @get:Input
    abstract val javaExecutable: Property<String>

    @get:OutputFile
    abstract val cache: RegularFileProperty

    @TaskAction
    fun train() {
        cache.get().asFile.parentFile.mkdirs()
        execution.exec {
            commandLine(
                javaExecutable.get(),
                // Keep compressed references compatible when the runtime heap address changes.
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+AOTCompatibleOopCompression",
                "-XX:AOTCacheOutput=${cache.get().asFile}",
                "-jar",
                jar.get().asFile.absolutePath,
                "--no-pager",
                "--color=never",
                document.get().asFile.absolutePath,
            )
        }
    }
}

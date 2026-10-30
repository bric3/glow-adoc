// SPDX-License-Identifier: MPL-2.0

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Jardiff's separate shell-prefixed archive technique, with quoted executable paths. */
@CacheableTask
abstract class ExecutableJar : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun create() {
        val target = outputJar.get().asFile
        target.parentFile.mkdirs()
        target.outputStream().use { output ->
            val launcher = requireNotNull(ExecutableJar::class.java.getResourceAsStream("/java-launcher.sh"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .replace("@MINIMUM_JAVA_VERSION@", "27")
            output.write((launcher.trimEnd() + "\n\n").toByteArray(Charsets.UTF_8))
            inputJar.get().asFile.inputStream().use { it.copyTo(output) }
        }
        check(target.setExecutable(true, false)) { "Cannot make executable: $target" }
    }
}

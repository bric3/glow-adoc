// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/** Keeps upstream notices separate so duplicate names survive Shadow packaging. */
@CacheableTask
abstract class CollectDependencyNotices : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifacts: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun collect() {
        val root = destination.get().asFile
        root.deleteRecursively()
        root.mkdirs()

        for (artifact in artifacts.files.sortedBy { it.name }) {
            if (!artifact.name.endsWith(".jar")) continue

            ZipFile(artifact).use { archive ->
                for (entry in archive.entries()) {
                    val name = entry.name.substringAfterLast('/').lowercase()
                    if (entry.isDirectory || name.endsWith(".class") ||
                        !name.matches(Regex("(licen[cs]e|notice|copying|copyright|authors)([._-].*)?"))
                    ) {
                        continue
                    }

                    val target = root.resolve(artifact.name.removeSuffix(".jar")).resolve(entry.name).normalize()
                    require(target.toPath().startsWith(root.toPath())) { "Invalid notice path: ${entry.name}" }
                    target.parentFile.mkdirs()
                    archive.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                }
            }
        }
    }
}

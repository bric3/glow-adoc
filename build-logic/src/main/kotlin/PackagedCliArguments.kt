// SPDX-License-Identifier: MPL-2.0

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class PackagedCliArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val executable: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixture: RegularFileProperty

    override fun asArguments() = listOf(
        "-Dglow.jar=${jar.get().asFile.absolutePath}",
        "-Dglow.executable=${executable.get().asFile.absolutePath}",
        "-Dglow.fixture=${fixture.get().asFile.absolutePath}",
        "-Dlauncher.executable=${executable.get().asFile.absolutePath}",
        "-Dlauncher.minimum=27",
    )
}

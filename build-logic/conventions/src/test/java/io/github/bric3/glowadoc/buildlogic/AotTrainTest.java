// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

class AotTrainTest {
    @TempDir
    Path directory;

    @Test
    void tracksTrainingInputsAndRestoresThePackagedCache() throws Exception {
        Files.writeString(
                directory.resolve("settings.gradle.kts"),
                """
            rootProject.name = "aot-test"
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        version("junit", "5.14.1")
                        library("tabletest", "org.tabletest", "tabletest-junit").version("1.2.2")
                    }
                }
            }
            buildCache { local { directory = file("task-cache") } }
            """
        );
        Files.writeString(
                directory.resolve("build.gradle.kts"),
                """
            import io.github.bric3.glowadoc.buildlogic.AotTrain
            plugins { id("glowadoc.java") }
            tasks.register<AotTrain>("aotTrain") {
                jar = layout.projectDirectory.file("input.jar")
                runtimeIdentity = providers.gradleProperty("trainingOptions").orElse("default-runtime")
                cacheDirectory = layout.buildDirectory.dir("aot")
            }
            """
        );
        Files.writeString(
                directory.resolve("input.jar"),
                """
            #!/bin/sh
            test "$1" = --train-aot || exit 1
            mkdir -p "$XDG_CACHE_HOME/glow-adoc/aot"
            printf trained > "$XDG_CACHE_HOME/glow-adoc/aot/generation-runtime.aot"
            printf x >> '%s'
            """
                    .formatted(directory.resolve("training-count"))
        );
        assertTrue(directory.resolve("input.jar").toFile().setExecutable(true));

        assertEquals(TaskOutcome.SUCCESS, train().task(":aotTrain").getOutcome());
        assertEquals("trained", Files.readString(directory.resolve("build/aot/cache.aot")));
        assertEquals("generation-runtime\n", Files.readString(directory.resolve("build/aot/cache.key")));
        assertEquals(TaskOutcome.UP_TO_DATE, train().task(":aotTrain").getOutcome());
        try (var files = Files.walk(directory.resolve("build"))) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
        var restored = train();
        assertEquals(TaskOutcome.FROM_CACHE, restored.task(":aotTrain").getOutcome());
        assertTrue(restored.getOutput().contains("Reusing configuration cache"));
        assertEquals("trained", Files.readString(directory.resolve("build/aot/cache.aot")));
        assertEquals(TaskOutcome.SUCCESS, train("-PtrainingOptions=-Xmx128m").task(":aotTrain").getOutcome());
        assertEquals("xx", Files.readString(directory.resolve("training-count")));
    }

    private org.gradle.testkit.runner.BuildResult train(String... options) {
        var args = new java.util.ArrayList<>(java.util.List.of(
                "aotTrain",
                "--build-cache",
                "--configuration-cache",
                "--offline",
                "--stacktrace"
        ));
        args.addAll(java.util.List.of(options));
        return GradleRunner
            .create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(args)
            .build();
    }
}

// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class VersioningTest {
    @TempDir Path directory;

    @Test
    void versionsUntaggedTaggedDirtyAndSubsequentCommits() throws Exception {
        Files.writeString(
                directory.resolve("settings.gradle.kts"),
                """
                plugins { id("glow.versioning") }
                rootProject.name = "version-check"
                include("child")
                """);
        Files.createDirectory(directory.resolve("child"));
        Files.writeString(
                directory.resolve("gradle.properties"),
                "semver.project.tagPrefix=v\nsemver.logOnlyOnRootProject=true\n");
        Files.writeString(directory.resolve(".gitignore"), ".gradle/\nbuild/\n.gradle-test-kit/\n");
        String build =
                """
                tasks.register("verifyVersion") {
                    val actual = project.version.toString()
                    doLast { println("VERSION=" + actual) }
                }
                """;
        Files.writeString(directory.resolve("build.gradle.kts"), build);
        Files.writeString(directory.resolve("child/build.gradle.kts"), build);
        git("init", "-b", "master");
        git("add", ".");
        git("-c", "user.name=Test", "-c", "user.email=test@example.org", "commit", "-m", "Initial");
        assertVersion("0.1.0-SNAPSHOT");
        assertTrue(run().contains("Reusing configuration cache"));
        assertTrue(
                run("-Psemver.scope=minor", "-Psemver.tagPrefix=v")
                        .contains("VERSION=0.2.0-SNAPSHOT"));
        assertEquals("", git("tag").strip());
        git("tag", "v0.1.0");
        assertVersion("0.1.0");
        assertTrue(
                run("-Psemver.scope=minor", "-Psemver.tagPrefix=v")
                        .contains("VERSION=0.2.0-SNAPSHOT"));
        Files.writeString(directory.resolve("dirty.txt"), "dirty");
        assertVersion("0.1.1-SNAPSHOT");
        git("add", ".");
        git("-c", "user.name=Test", "-c", "user.email=test@example.org", "commit", "-m", "Change");
        assertVersion("0.1.1-SNAPSHOT");
        assertTrue(
                run("-Psemver.scope=minor", "-Psemver.tagPrefix=v")
                        .contains("VERSION=0.2.0-SNAPSHOT"));
    }

    private void assertVersion(String version) {
        String output = run();
        assertEquals(
                2,
                output.lines().filter(line -> line.equals("VERSION=" + version)).count(),
                output);
    }

    private String run(String... extra) {
        var args =
                new ArrayList<>(
                        List.of(
                                ":verifyVersion",
                                ":child:verifyVersion",
                                "--configuration-cache",
                                "--stacktrace"));
        args.addAll(Arrays.asList(extra));
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withArguments(args)
                .build()
                .getOutput();
    }

    private String git(String... args) throws Exception {
        var command =
                new ArrayList<>(
                        List.of(
                                "git",
                                "-c",
                                "tag.gpgSign=false",
                                "-c",
                                "commit.gpgSign=false",
                                "-c",
                                "core.hooksPath=/dev/null",
                                "-C",
                                directory.toString()));
        command.addAll(Arrays.asList(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output =
                new String(
                        process.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @TempDir
    Path directory;

    @Test
    void versionsUntaggedTaggedDirtyAndSubsequentCommits() throws Exception {
        Files.writeString(
                directory.resolve("settings.gradle.kts"),
                """
                plugins { id("glowadoc.versioning") }
                rootProject.name = "version-check"
                include("child")
                """
        );
        Files.createDirectory(directory.resolve("child"));
        Files.writeString(
                directory.resolve("gradle.properties"),
                "semver.project.tagPrefix=v\nsemver.logOnlyOnRootProject=true\n"
        );
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
        assertTrue(run("-Psemver.scope=minor", "-Psemver.tagPrefix=v").contains("VERSION=0.2.0-SNAPSHOT"));
        assertEquals("", git("tag").strip());
        git("tag", "v0.1.0");
        assertVersion("0.1.0");
        assertTrue(run("-Psemver.scope=minor", "-Psemver.tagPrefix=v").contains("VERSION=0.2.0-SNAPSHOT"));
        Files.writeString(directory.resolve("dirty.txt"), "dirty");
        assertVersion("0.1.1-SNAPSHOT");
        git("add", ".");
        git("-c", "user.name=Test", "-c", "user.email=test@example.org", "commit", "-m", "Change");
        assertVersion("0.1.1-SNAPSHOT");
        assertTrue(run("-Psemver.scope=minor", "-Psemver.tagPrefix=v").contains("VERSION=0.2.0-SNAPSHOT"));

        assertTrue(runner("-PreleaseTag=v0.1.0").buildAndFail().getOutput().contains("must check out v0.1.0"));
        git("tag", "v0.2.0");
        assertTrue(run("-PreleaseTag=v0.2.0", "-PreleasePrerelease=false").contains("VERSION=0.2.0"));
        assertTrue(run("-PreleaseTag=v0.2.0", "-PreleasePrerelease=false").contains("Reusing configuration cache"));
        assertTrue(run("-PreleaseTag=v0.2.0", "-PreleasePrerelease=").contains("VERSION=0.2.0"));
        git("tag", "v0.3.0-rc.1");
        assertTrue(run("-PreleaseTag=v0.3.0-rc.1", "-PreleasePrerelease=true").contains("VERSION=0.3.0-rc.1"));
        Files.writeString(directory.resolve("dirty.txt"), "changed");
        assertTrue(runner("-PreleaseTag=v0.2.0").buildAndFail().getOutput().contains("require a clean checkout"));
    }

    @Test
    void validatesReleaseTagsAndPrereleaseFlags() {
        assertEquals("0.2.0", ReleaseVersionMapper.Companion.releaseVersion("v0.2.0", false));
        assertEquals("0.2.0-rc.1", ReleaseVersionMapper.Companion.releaseVersion("v0.2.0-rc.1", true));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionMapper.Companion.releaseVersion("0.2.0", false));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionMapper.Companion.releaseVersion(
                "v0.2.0-SNAPSHOT",
                true
        ));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionMapper.Companion.releaseVersion(
                "v0.2.0+build",
                false
        ));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionMapper.Companion.releaseVersion("v0.2.0", true));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersionMapper.Companion.releaseVersion(
                "v0.2.0-rc.1",
                false
        ));
    }

    private void assertVersion(String version) {
        String output = run();
        assertEquals(2, output
            .lines()
            .filter(line -> line.equals("VERSION=" + version))
            .count(), output);
    }

    private String run(String... extra) {
        return runner(extra).build().getOutput();
    }

    private GradleRunner runner(String... extra) {
        var args =
                new ArrayList<>(List.of(
                        ":verifyVersion",
                        ":child:verifyVersion",
                        "--configuration-cache",
                        "--stacktrace"
        ));
        args.addAll(Arrays.asList(extra));
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(args);
    }

    private String git(String... args) throws Exception {
        var command = new ArrayList<>(List.of(
                "git",
                "-c",
                "tag.gpgSign=false",
                "-c",
                "commit.gpgSign=false",
                "-c",
                "core.hooksPath=/dev/null",
                "-C",
                directory.toString()
        ));
        command.addAll(Arrays.asList(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}

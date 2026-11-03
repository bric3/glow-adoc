// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.buildlogic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

class InstallExecutableTest {
    @TempDir
    Path directory;

    @Test
    void installsASmallLauncherAndPreservesTheJarAndItsTimestamp() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        Files.writeString(
                directory.resolve("settings.gradle.kts"),
                """
                rootProject.name = "install-test"
                dependencyResolutionManagement {
                    versionCatalogs {
                        create("libs") {
                            version("junit", "5.14.1")
                            library("tabletest", "org.tabletest", "tabletest-junit").version("1.2.2")
                        }
                    }
                }
                """
        );
        Files.writeString(
                directory.resolve("build.gradle.kts"),
                """
                import io.github.bric3.glowadoc.buildlogic.InstallExecutable
                plugins { id("glowadoc.java") }
                tasks.register<InstallExecutable>("installTest") {
                    jar = layout.projectDirectory.file("input.jar")
                    launcherScript = layout.projectDirectory.file("install-launcher.sh")
                    executable = layout.projectDirectory.file("home with 'quotes'/.local/bin/glow-adoc")
                    installedJar = layout.projectDirectory.file("home with 'quotes'/.local/share/glow-adoc/glow-adoc.jar")
                    installedAot = layout.projectDirectory.dir("home with 'quotes'/.local/share/glow-adoc/aot")
                    if (providers.gradleProperty("withAot").isPresent) {
                        aotCache = layout.projectDirectory.dir("aot-input")
                    }
                }
                """
        );
        Files.writeString(directory.resolve("install-launcher.sh"), "#!/bin/sh\nexec /bin/sh @JAR@ \"$@\"\n");
        Path input = directory.resolve("input.jar");
        Files.writeString(input, "#!/bin/sh\nprintf '%s\\n' \"$0\" \"$@\"\nexit 0\n");
        Files.write(input, new byte[2_000_000], StandardOpenOption.APPEND);
        Files.setLastModifiedTime(input, FileTime.fromMillis(1_600_000_000_000L));
        install();
        Path home = directory.resolve("home with 'quotes'");
        Path launcher = home.resolve(".local/bin/glow-adoc");
        Path installedJar = home.resolve(".local/share/glow-adoc/glow-adoc.jar");
        assertTrue(Files.isExecutable(launcher));
        assertTrue(Files.size(launcher) < 1_024);
        assertArrayEquals(Files.readAllBytes(input), Files.readAllBytes(installedJar));
        assertEquals(Files.getLastModifiedTime(input), Files.getLastModifiedTime(installedJar));

        Path output = directory.resolve("output.txt");
        var process = new ProcessBuilder(launcher.toString(), "a file.adoc", "$(touch should-not-exist)")
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(output));
            List<String> actual = Files.readAllLines(output);
            assertTrue(Files.isSameFile(installedJar, Path.of(actual.getFirst())));
            assertEquals(List.of("a file.adoc", "$(touch should-not-exist)"), actual.subList(1, actual.size()));
            assertTrue(Files.notExists(directory.resolve("should-not-exist")));
        } finally {
            process.destroyForcibly();
        }

        Files.writeString(input, "replacement jar");
        assertTrue(install().contains("Reusing configuration cache"));
        assertEquals("replacement jar", Files.readString(installedJar));
        assertTrue(Files.isExecutable(launcher));

        Path cache = Files.createDirectory(directory.resolve("aot-input"));
        Files.writeString(cache.resolve("cache.aot"), "trained cache");
        Files.writeString(cache.resolve("cache.key"), "cache identity");
        install("-PwithAot");
        Path installedCache = installedJar.getParent().resolve("aot");
        assertEquals("trained cache", Files.readString(installedCache.resolve("cache.aot")));
        assertEquals("cache identity", Files.readString(installedCache.resolve("cache.key")));
        assertEquals(318211200000L, Files.getLastModifiedTime(installedJar).toMillis());
        install();
        assertTrue(Files.notExists(installedCache.resolve("cache.aot")));
        assertTrue(Files.notExists(installedCache.resolve("cache.key")));
    }

    private String install(String... options) {
        var args =
                new java.util.ArrayList<>(List.of("installTest", "--configuration-cache", "--offline", "--stacktrace"));
        args.addAll(List.of(options));
        return GradleRunner
            .create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(args)
            .build()
            .getOutput();
    }
}

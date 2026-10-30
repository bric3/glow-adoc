// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;

class PackagedCliTest {
    @TempDir Path directory;
    private final String java = Path.of(System.getProperty("java.home"), "bin/java").toString();

    @Test
    void packagesProjectLicenceAndDistinctUpstreamNotices() throws Exception {
        try (var jar = new JarFile(System.getProperty("glow.jar"))) {
            assertTrue(jar.getEntry("META-INF/glow-adoc/LICENSE") != null);
            assertTrue(jar.getEntry("META-INF/glow-adoc/THIRD-PARTY.md") != null);
            assertTrue(jar.getEntry("META-INF/third-party/plantuml-mit.txt") != null);
            assertTrue(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .startsWith(
                                                            "META-INF/third-party/jruby-stdlib-")));
            assertTrue(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName()
                                                    .startsWith(
                                                            "META-INF/third-party/joda-time-")));
            assertFalse(
                    jar.stream()
                            .anyMatch(
                                    entry ->
                                            entry.getName().startsWith("META-INF/third-party/")
                                                    && entry.getName().endsWith(".class")));
        }
    }

    @Test
    void shadedJarRendersAndExecutableHandlesSpaces() throws Exception {
        var process =
                new ProcessBuilder(
                                java,
                                "-jar",
                                System.getProperty("glow.jar"),
                                "--no-pager",
                                "--color=never",
                                System.getProperty("glow.fixture"))
                        .redirectError(directory.resolve("errors").toFile())
                        .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), Files.readString(directory.resolve("errors")));
        assertTrue(output.contains("glow-adoc showcase"));
        assertTrue(output.contains("System.out.println"));
        assertFalse(output.contains("\u001b"));
        Path executable = directory.resolve("reader with spaces.jar");
        Files.copy(Path.of(System.getProperty("glow.executable")), executable);
        assertTrue(executable.toFile().setExecutable(true));
        var versionBuilder =
                new ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true);
        versionBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        var version = versionBuilder.start();
        String versionOutput = new String(version.getInputStream().readAllBytes());
        assertTrue(version.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, version.exitValue(), versionOutput);
        assertTrue(versionOutput.contains("glow-adoc"));
    }

    @Test
    void stdinAndConfigurationOverridesWork() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "color = 'always'\nwidth = 4\n");
        var process =
                new ProcessBuilder(
                                java,
                                "-jar",
                                System.getProperty("glow.jar"),
                                "--config",
                                config.toString(),
                                "--no-pager",
                                "--color=never",
                                "--width=80",
                                "-")
                        .redirectError(directory.resolve("errors").toFile())
                        .start();
        process.getOutputStream().write("A *bold* line".getBytes());
        process.getOutputStream().close();
        String text = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertEquals("A bold line\n\n", text);
    }

    @Test
    void pagerAcceptsSearchAndQuitInPseudoTerminal() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isExecutable(Path.of("/usr/bin/script")));
        Path transcript = directory.resolve("transcript");
        var process = startPty(System.getProperty("glow.fixture"), transcript, false);
        try {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(30),
                    () -> {
                        await(process, transcript, "quit");
                        process.getOutputStream().write("/Source\nnq".getBytes());
                        process.getOutputStream().flush();
                        assertTrue(
                                process.waitFor(10, TimeUnit.SECONDS),
                                Files.readString(transcript));
                        assertEquals(0, process.exitValue(), Files.readString(transcript));
                        assertTrue(
                                Files.readString(transcript).contains("\u001b[?1049l"),
                                "Pager must leave alternate screen");
                    });
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void handlesThemeNotificationsAndKittyImages() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isExecutable(Path.of("/usr/bin/script")));
        var image = new BufferedImage(80, 32, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", directory.resolve("picture.png").toFile());
        Path document = directory.resolve("image.adoc");
        Files.writeString(document, "= Image\n\nimage::picture.png[]\n");
        Path transcript = directory.resolve("graphics-transcript");
        var process = startPty(document.toString(), transcript, true);
        try {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(30),
                    () -> {
                        await(process, transcript, "quit");
                        process.getOutputStream()
                                .write(
                                        ("\u001b[?2031;2$y\u001b[?997;1n\u001b]11;rgb:1010/1010/1010\u001b\\"
                                             + "\u001b_Gi=2147483000;OK\u001b\\")
                                                .getBytes());
                        process.getOutputStream().flush();
                        await(process, transcript, "a=T,t=d,f=100");
                        process.getOutputStream()
                                .write(
                                        "\u001b[?997;2n\u001b]11;rgb:ffff/ffff/ffff\u001b\\"
                                                .getBytes());
                        process.getOutputStream().flush();
                        await(process, transcript, "255;255;255");
                        process.getOutputStream().write('q');
                        process.getOutputStream().flush();
                        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                        assertEquals(0, process.exitValue(), Files.readString(transcript));
                        assertTrue(
                                Files.readString(transcript).contains("\u001b[?2031l"),
                                "Restore theme notification mode");
                        assertTrue(
                                Files.readString(transcript).contains("a=d,d=I"),
                                "Remove application-owned images");
                    });
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private Process startPty(String document, Path transcript, boolean images) throws IOException {
        List<String> command = new ArrayList<>(List.of("/usr/bin/script", "-q"));
        if (System.getProperty("os.name").startsWith("Mac")) {
            command.addAll(
                    List.of(
                            "/dev/null",
                            java,
                            "-jar",
                            System.getProperty("glow.jar"),
                            "--images=" + (images ? "auto" : "never"),
                            document));
        } else {
            command.addAll(
                    List.of(
                            "-e",
                            "-c",
                            quote(java)
                                    + " -jar "
                                    + quote(System.getProperty("glow.jar"))
                                    + " --images="
                                    + (images ? "auto" : "never")
                                    + " "
                                    + quote(document),
                            "/dev/null"));
        }
        var builder =
                new ProcessBuilder(command)
                        .redirectOutput(transcript.toFile())
                        .redirectErrorStream(true);
        builder.environment().put("TERM", "xterm-256color");
        builder.environment().remove("NO_COLOR");
        return builder.start();
    }

    private static void await(Process process, Path transcript, String text) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (Files.exists(transcript) && Files.readString(transcript).contains(text)) {
                return;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(50);
        }
        fail("Missing " + text + ": " + Files.readString(transcript));
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }
}

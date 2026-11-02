// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

class PackagedCliTest {
    @TempDir
    Path directory;
    private final String java = Path.of(System.getProperty("java.home"), "bin/java").toString();

    @Test
    void distributionZipRunsAfterExtractionRelocationAndSymlinking() throws Exception {
        String distribution = System.getProperty("glow.distribution");
        assertNotNull(distribution, "A ZIP distribution must be supplied to packaged tests");
        String version;
        try (var jar = new JarFile(System.getProperty("glow.jar"))) {
            version = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
        }
        Path extracted = directory.resolve("downloaded release");
        var unzip = new ProcessBuilder("unzip", "-q", distribution, "-d", extracted.toString())
            .redirectErrorStream(true)
            .start();
        try {
            assertTrue(unzip.waitFor(30, TimeUnit.SECONDS));
            assertEquals(
                    0,
                    unzip.exitValue(),
                    new String(unzip.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            );
        } finally {
            unzip.destroyForcibly();
        }
        try (var entries = Files.list(extracted)) {
            assertEquals(List.of("glow-adoc-" + version), entries
                .map(path -> path.getFileName().toString())
                .toList());
        }
        Path home =
                Files.move(extracted.resolve("glow-adoc-" + version), directory.resolve("relocated app with spaces"));
        Path launcher = home.resolve("bin/glow-adoc");
        assertTrue(Files.isExecutable(launcher), "The ZIP preserves executable permissions");
        assertEquals(-1L, Files.mismatch(Path.of(System.getProperty("glow.jar")), home.resolve("lib/glow-adoc.jar")));
        Path link = Files.createSymbolicLink(directory.resolve("glow-adoc-link"), directory.relativize(launcher));
        Path output = directory.resolve("distribution-output");
        for (Path executable : List.of(launcher, link)) {
            var builder = new ProcessBuilder(executable.toString(), "--version")
                .directory(directory.toFile())
                .redirectOutput(output.toFile())
                .redirectErrorStream(true);
            selectTestJava(builder);
            var process = builder.start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS));
                assertEquals(0, process.exitValue(), Files.readString(output));
                assertEquals("glow-adoc " + version, Files.readString(output).strip());
            } finally {
                process.destroyForcibly();
            }
        }
        Path document = directory.resolve("document with spaces.adoc");
        Files.writeString(document, "= Download\n\nA *bold* sentence.\n");
        var builder = new ProcessBuilder(
                link.toString(),
                "--no-pager",
                "--no-cache",
                "--color=never",
                document.toString()
        )
            .directory(directory.toFile())
            .redirectOutput(output.toFile())
            .redirectErrorStream(true);
        selectTestJava(builder);
        builder.environment().put("XDG_CONFIG_HOME", directory.resolve("config").toString());
        var process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(output));
            assertTrue(Files.readString(output).contains("A bold sentence."));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void defaultsTo120ColumnsAndKeepsYamlFrontMatterLiteral() throws Exception {
        var process = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-pager",
                "--color=never",
                "--icons=ascii",
                "-"
        )
            .redirectError(directory.resolve("width-errors").toFile())
            .start();
        process
            .getOutputStream()
            .write(("---\ntitle: An _unchanged_ title\ntags:\n  - java\n---\n\n" + "word ".repeat(80) + "\n")
                .getBytes(StandardCharsets.UTF_8)
            );
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), Files.readString(directory.resolve("width-errors")));
        assertTrue(output.startsWith("title: An _unchanged_ title\ntags:\n  - java\n"), output);
        assertTrue(output.contains("\n" + "-".repeat(120) + "\n"), output);
        assertFalse(output.contains("+--"), output);
        var prose = output
            .lines()
            .filter(line -> line.startsWith("word"))
            .toList();
        assertEquals(4, prose.size(), output);
        assertTrue(prose.stream().allMatch(line -> line.length() <= 120));
        assertTrue(prose.stream().anyMatch(line -> line.length() > 80));
    }

    @Test
    void persistentHighlightsAvoidLoadingWasmAndPreserveOutput() throws Exception {
        Path input = directory.resolve("highlighted.adoc");
        Files.writeString(input, "[source,java]\n----\nclass Example { int answer = 42; }\n----\n");
        Path output = directory.resolve("highlight-output");
        Path classes = directory.resolve("loaded-classes");
        String expected = null;
        for (String mode : List.of("miss", "hit", "bypass")) {
            var command = new ArrayList<>(List.of(
                    java,
                    "--sun-misc-unsafe-memory-access=allow",
                    "-Xlog:class+load=info:file=" + classes,
                    "-jar",
                    System.getProperty("glow.jar"),
                    "--no-pager",
                    "--color=always"
            ));
            if (mode.equals("bypass")) {
                command.add("--no-cache");
            }
            command.add(input.toString());
            var builder = new ProcessBuilder(command)
                .redirectOutput(output.toFile())
                .redirectError(directory.resolve("highlight-errors").toFile());
            builder.environment().put("XDG_CACHE_HOME", directory.resolve("cache-home").toString());
            builder.environment().put("XDG_CONFIG_HOME", directory.resolve("config-home").toString());
            var process = builder.start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS));
                assertEquals(0, process.exitValue(), Files.readString(directory.resolve("highlight-errors")));
                String text = Files.readString(output);
                assertTrue(text.contains("38;2;"), "Source has syntax colours");
                if (expected == null) {
                    expected = text;
                }
                assertEquals(expected, text, mode);
                assertEquals(
                        !mode.equals("hit"),
                        Files.readString(classes).contains("com.dylibso.chicory.wasm.Parser"),
                        mode
                );
            } finally {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void pagerDisplaysTextBeforeSlowDiagramsAndQuitsWithoutWaiting() throws Exception {
        Path command = directory.resolve("slow-merman");
        Files.writeString(command, "#!/bin/sh\nexec sleep 30\n");
        assertTrue(command.toFile().setExecutable(true));
        Path config = directory.resolve("diagrams.toml");
        Files.writeString(config, "merman = '" + command + "'\n");
        Path input = directory.resolve("diagrams.adoc");
        Files.writeString(input, "FIRST_TEXT\n\n[mermaid]\n....\nflowchart LR; A --> B\n....\n");
        Path transcript = directory.resolve("diagram-transcript");
        var process = startPty(input.toString(), transcript, true, "--config", config.toString(), "--timings");
        try {
            await(process, transcript, "a=q,t=d,f=24");
            process.getOutputStream().write("\u001B_Gi=2147483000;OK\u001B\\".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "timing first-frame:");
            assertTrue(screen(Files.readString(transcript)).contains("FIRST_TEXT"));
            assertFalse(Files.readString(transcript).contains("Merman timed out"));
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void conversionCacheSkipsJrubyAndInvalidatesContentAndAttributes() throws Exception {
        Path input = directory.resolve("cached.adoc");
        Files.writeString(input, "= Cached\n\n{name}\n");
        var modified = Files.getLastModifiedTime(input);
        String first = cachedRead(input, "-a", "name=first");
        String expected = Files.readString(directory.resolve("cache-output"));
        assertTrue(first.contains("timing jruby-ready:"));
        String hit = cachedRead(input, "-a", "name=first");
        assertTrue(hit.contains("timing conversion-cache-hit:"));
        assertFalse(hit.contains("timing jruby-ready:"));
        assertEquals(expected, Files.readString(directory.resolve("cache-output")));
        assertTrue(cachedRead(input, "--no-cache", "-a", "name=first").contains("timing jruby-ready:"));
        assertTrue(cachedRead(input, "-a", "name=changed").contains("timing jruby-ready:"));
        assertTrue(Files.readString(directory.resolve("cache-output")).contains("changed"));
        Files.writeString(input, "= Edited\n\n{name}\n");
        Files.setLastModifiedTime(input, modified);
        assertTrue(cachedRead(input, "-a", "name=changed").contains("timing jruby-ready:"));
        assertTrue(Files.readString(directory.resolve("cache-output")).contains("Edited"));
        Path patched = directory.resolve("patched.jar");
        Files.copy(Path.of(System.getProperty("glow.jar")), patched);
        try (var archive = FileSystems.newFileSystem(patched)) {
            Files.writeString(archive.getPath("/cache-test-marker"), "changed");
        }
        assertTrue(cachedReadWithJar(input, patched.toString(), "-a", "name=changed").contains("timing jruby-ready:"));
        Files.writeString(input, "include::part.adoc[]\n");
        Path part = Files.writeString(directory.resolve("part.adoc"), "FIRST\n");
        assertTrue(cachedRead(input).contains("timing jruby-ready:"));
        assertTrue(Files.readString(directory.resolve("cache-output")).contains("FIRST"));
        Files.writeString(part, "SECOND\n");
        assertTrue(cachedRead(input).contains("timing jruby-ready:"));
        assertTrue(Files.readString(directory.resolve("cache-output")).contains("SECOND"));
    }

    private String cachedRead(Path input, String... options) throws Exception {
        return cachedReadWithJar(input, System.getProperty("glow.jar"), options);
    }

    private String cachedReadWithJar(Path input, String jar, String... options) throws Exception {
        var command = new ArrayList<>(List.of(
                java,
                "--sun-misc-unsafe-memory-access=allow",
                "-jar",
                jar,
                "--timings",
                "--no-pager",
                "--color=never"
        ));
        command.addAll(List.of(options));
        command.add(input.toString());
        Path errors = directory.resolve("cache-errors");
        var builder = new ProcessBuilder(command)
            .redirectError(errors.toFile())
            .redirectOutput(directory.resolve("cache-output").toFile());
        builder.environment().put("XDG_CACHE_HOME", directory.resolve("cache-home").toString());
        builder.environment().put("XDG_CONFIG_HOME", directory.resolve("config-home").toString());
        var process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(errors));
            return Files.readString(errors);
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void packagesProjectLicenceAndDistinctUpstreamNotices() throws Exception {
        try (var jar = new JarFile(System.getProperty("glow.jar"))) {
            assertTrue(jar.getEntry("META-INF/glow-adoc/LICENSE") != null);
            assertTrue(jar.getEntry("META-INF/glow-adoc/THIRD-PARTY.md") != null);
            assertTrue(jar.getEntry("META-INF/third-party/plantuml-mit.txt") != null);
            assertTrue(jar
                .stream()
                .anyMatch(entry -> entry.getName().startsWith("META-INF/third-party/jruby-stdlib-")));
            assertTrue(jar
                .stream()
                .anyMatch(entry -> entry.getName().startsWith("META-INF/third-party/joda-time-")));
            assertFalse(jar
                .stream()
                .anyMatch(entry -> entry.getName().startsWith("META-INF/third-party/")
                        && entry.getName().endsWith(".class"))
            );
        }
    }

    @Test
    void shadedJarRendersAndExecutableHandlesSpaces() throws Exception {
        var process = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-pager",
                "--color=never",
                System.getProperty("glow.fixture")
        )
            .redirectError(directory.resolve("errors").toFile())
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), Files.readString(directory.resolve("errors")));
        assertTrue(output.contains("glow-adoc showcase"));
        assertTrue(output.contains("System.out.println"));
        assertFalse(output.contains("\u001B"));
        Path executable = directory.resolve("reader with spaces.jar");
        Files.copy(Path.of(System.getProperty("glow.executable")), executable);
        assertTrue(executable.toFile().setExecutable(true));
        var versionBuilder = new ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true);
        selectTestJava(versionBuilder);
        var version = versionBuilder.start();
        String versionOutput = new String(version.getInputStream().readAllBytes());
        assertTrue(version.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, version.exitValue(), versionOutput);
        assertTrue(versionOutput.contains("glow-adoc"));
    }

    @Test
    void bundledAotWorkloadRunsWithoutAnExternalDocumentOrConfiguration() throws Exception {
        Files.writeString(directory.resolve(".glow-adoc.toml"), "invalid = [\n");
        try (var archive = new JarFile(System.getProperty("glow.jar"))) {
            var resource = archive.getJarEntry("META-INF/glow-adoc/aot/workload.adoc");
            assertTrue(resource != null, "Bundled workload missing from the packaged jar");
            try (var input = archive.getInputStream(resource)) {
                assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("glow-adoc AOT corpus"));
            }
        }
        Path errors = directory.resolve("workload-errors");
        var process = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-cache",
                "--no-pager",
                "--color=never",
                "--aot-workload"
        )
            .directory(directory.toFile())
            .redirectError(errors.toFile())
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), Files.readString(errors));
        for (String marker :
                List.of(
                        "glow-adoc AOT corpus",
                        "Reading service diagnostics",
                        "Bounded diagnostic sessions",
                        "Diagnostic tables",
                        "CompletableFuture",
                        "Embedded HTML",
                        "flowchart LR"
        )) {
            assertTrue(output.contains(marker), marker + ": " + output);
        }
        assertFalse(output.contains("[include unavailable:"), output);
    }

    @TableTest("""
        colour
        true
        false
        """)
    void rendersAdmonitionTitleBarsWithAndWithoutColour(boolean colour) throws Exception {
        var process = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-pager",
                "--width=32",
                "--icons=ascii",
                "--theme=dark",
                "--color=" + (colour ? "always" : "never"),
                "-"
        )
            .redirectError(directory.resolve("admonition-errors").toFile())
            .start();
        try {
            process.getOutputStream().write("CAUTION: Mind the gap.\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(directory.resolve("admonition-errors")));
            String text = output.replaceAll("\u001B\\[[0-9;]*m", "");
            assertTrue(text.startsWith("+-- ! CAUTION "), text);
            assertTrue(text.contains("Mind the gap."), text);
            if (colour) {
                assertTrue(output.contains("48;2;210;153;34"), "CAUTION has an amber background");
            } else {
                assertFalse(output.contains("\u001B"), "Monochrome output has no terminal controls");
            }
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void stdinAndConfigurationOverridesWork() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "color = 'always'\nwidth = 4\n");
        var process = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--config",
                config.toString(),
                "--no-pager",
                "--color=never",
                "--width=80",
                "-"
        )
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
    void discoversDocumentConfigurationAndAcceptsRepeatedLiteralAttributes() throws Exception {
        Path site = Files.createDirectory(directory.resolve("attribute-site"));
        Path posts = Files.createDirectories(site.resolve("content/posts"));
        Path document = posts.resolve("post.adoc");
        Files.writeString(document, "= Attributes\n:name: document\n\n{name} / {other} / {literal}\n");
        Files.writeString(site.resolve(".glow-adoc.toml"), "[attributes]\nname = 'project'\nother = 'normal'\n");
        Files.writeString(site.resolve(".glow-adoc.local.toml"), "[attributes]\nname = 'local'\nother = 'kept'\n");
        var builder = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-pager",
                "--color=never",
                "-a",
                "name=first",
                "--attribute",
                "name=cli",
                "--attribute=literal=a,b=c",
                document.toString()
        )
            .directory(directory.toFile())
            .redirectErrorStream(true);
        builder.environment().put("XDG_CONFIG_HOME", directory.resolve("no-user-config").toString());
        var process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("cli / kept / a,b=c"), output);
        var stdinBuilder = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-pager",
                "--color=never",
                "-"
        )
            .directory(site.toFile())
            .redirectErrorStream(true);
        stdinBuilder.environment().put("XDG_CONFIG_HOME", directory.resolve("no-user-config").toString());
        var stdin = stdinBuilder.start();
        stdin.getOutputStream().write("{name} / {other}".getBytes(StandardCharsets.UTF_8));
        stdin.getOutputStream().close();
        String stdinOutput = new String(stdin.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(stdin.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, stdin.exitValue(), stdinOutput);
        assertTrue(stdinOutput.contains("local / kept"), stdinOutput);
    }

    @TableTest(
            """
        projectConfig | absoluteAttribute
        true          | false
        false         | false
        false         | true
        """
    )
    void standaloneInlineImagesUseAttributesAndConfiguredRootsInThePager(
            boolean projectConfig,
            boolean absoluteAttribute
    ) throws Exception {
        Path site = Files.createDirectory(directory.resolve("image-site"));
        Path posts = Files.createDirectories(site.resolve("content/posts"));
        Path assets = Files.createDirectories(site.resolve("static/assets"));
        ImageIO.write(
                new BufferedImage(80, 32, BufferedImage.TYPE_INT_RGB),
                "png",
                assets.resolve("graph.png").toFile()
        );
        if (projectConfig) {
            Files.writeString(
                    site.resolve(".glow-adoc.toml"),
                    "image-roots = ['static']\n[attributes]\nimage-assets = 'assets'\n"
            );
        }
        Path document = posts.resolve("post.adoc");
        Files.writeString(document, "image:{image-assets}/graph.png[Configured flamegraph]\n");
        Path transcript = directory.resolve("inline-image-transcript");
        var process = startPty(
                document.toString(),
                transcript,
                true,
                projectConfig
                ? new String[0]
                : absoluteAttribute
                ? new String[] {"-a", "image-assets=" + assets}
                : new String[] {"--attribute=image-assets=assets", "--image-root=" + assets.getParent()}
        );
        try {
            await(process, transcript, "Configured flamegraph");
            process.getOutputStream().write("\u001B_Gi=2147483000;OK\u001B\\".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "a=p,i=");
            var upload = Pattern.compile("a=t,t=d,f=100,q=2,i=([0-9]+)").matcher(Files.readString(transcript));
            assertTrue(upload.find());
            process.getOutputStream().write(("\u001B_Gi=" + upload.group(1) + ";ENOENT:evicted\u001B\\").getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "a=t,t=d,f=100", 2);
            assertFalse(
                    screen(Files.readString(transcript)).contains("Configured flamegraph"),
                    "Image placement must replace its fallback description"
            );
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void pagerAcceptsSearchAndQuitInPseudoTerminal() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")));
        Path transcript = directory.resolve("transcript");
        var process = startPty(System.getProperty("glow.fixture"), transcript, false);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                await(process, transcript, "quit");
                process.getOutputStream().write("/Source\nnq".getBytes());
                process.getOutputStream().flush();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), Files.readString(transcript));
                assertEquals(0, process.exitValue(), Files.readString(transcript));
                assertTrue(Files.readString(transcript).contains("\u001B[?1049l"), "Pager must leave alternate screen");
            });
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @TableTest("""
        colour
        true
        false
        """)
    void searchRepaintsEveryMatchWhileTypingBeforeEnter(boolean colour) throws Exception {
        Path document = directory.resolve("live-search.adoc");
        Files.writeString(
                document,
                "First NEEDLE, another NEEDLE.\n\nThird NEEDLE.\n\n"
                + "[source,text]\n----\n"
                + "x".repeat(100)
                + " HIDDEN_MATCH\n----\n"
        );
        Path transcript = directory.resolve("live-search-transcript");
        var process =
                startPty(
                        document.toString(),
                        transcript,
                        false,
                        "--color=" + (colour ? "always" : "never"),
                        "--theme=dark"
        );
        try {
            await(process, transcript, "quit");
            assertFalse(screen(Files.readString(transcript)).contains("HIDDEN_MATCH"));
            int before = Files.readString(transcript).length();
            process.getOutputStream().write("/needle".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "/needle  1/3 matches");
            String redraw = Files.readString(transcript).substring(before);
            // Incremental frames repaint one newly matched character at a time.
            assertTrue(
                    Pattern.compile("N").matcher(redraw).results().count() >= 3,
                    "Every matching occurrence must be repainted without Enter: " + redraw
            );
            if (colour) {
                assertTrue(redraw.contains("48;2;121;192;255"), "Active match background");
                assertTrue(redraw.contains("48;2;45;51;59"), "Other match backgrounds");
            } else {
                assertTrue(
                        Pattern.compile("\u001B\\[[0-9;]*7[;m]").matcher(redraw).find(),
                        "Monochrome matches use reverse video"
                );
            }
            process.getOutputStream().write("\rnn".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "3/3 matches");
            process.getOutputStream().write('n');
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "1/3 matches");
            process.getOutputStream().write("/hidden_match".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "/hidden_match  1/1 matches");
            assertTrue(
                    screen(Files.readString(transcript)).contains("HIDDEN_MATCH"),
                    "Search must reveal matches beyond the horizontal viewport"
            );
            process.getOutputStream().write("\u007F".repeat(12).getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "First NEEDLE, another NEEDLE.");
            assertFalse(
                    screen(Files.readString(transcript)).contains("HIDDEN_MATCH"),
                    "Deleting the query restores the horizontal viewport"
            );
            process.getOutputStream().write(27);
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "q quit");
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void liveSearchUsesTheOriginalPositionAndPrefersTheMatchAbove() throws Exception {
        Path document = directory.resolve("nearest-search.adoc");
        Files.writeString(
                document,
                "Start\n\nNEEDLE above\n\nOrigin\n\nNEEDLE below\n\n"
                + IntStream.range(0, 40).mapToObj(i -> "Tail " + i + "\n\n").collect(Collectors.joining())
        );
        Path transcript = directory.resolve("nearest-search-transcript");
        var process = startPty(document.toString(), transcript, false);
        try {
            await(process, transcript, "quit");
            process.getOutputStream().write("jjjj".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 5/");
            process.getOutputStream().write("/needle".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "/needle  1/2 matches");
            assertTrue(screen(Files.readString(transcript)).contains(" 3/"));
            process.getOutputStream().write(27);
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "q quit");
            assertTrue(screen(Files.readString(transcript)).contains(" 5/"), "Escape restores the reading position");
            process.getOutputStream().write("/needle\r".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "1/2 matches");
            assertTrue(screen(Files.readString(transcript)).contains(" 3/"), "Enter retains the live match");
            process.getOutputStream().write('n');
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 7/");
            process.getOutputStream().write('N');
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 3/");
            process.getOutputStream().write("/absent".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "No match");
            process.getOutputStream().write(27);
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "q quit");
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @TableTest("""
        width | sameRow
        80    | true
        20    | false
        """)
    void rendersGroupedSvgBadgesAfterTextAndRetainsMouseLinks(int width, boolean sameRow) throws Exception {
        Files.writeString(
                directory.resolve("badge.svg"),
                "<svg xmlns='http://www.w3.org/2000/svg' width='90' height='20'><rect width='90' height='20' fill='green'/></svg>"
        );
        Path input = directory.resolve("badges.adoc");
        Files.writeString(
                input,
                """
                FIRST_TEXT

                image:badge.svg[Build,link=https://example.org/build]
                image:missing.svg[Missing,link=https://example.org/missing]
                image:badge.svg[License,link=https://example.org/license]

                AFTER_BADGES
                """
        );
        Path transcript = directory.resolve("badge-transcript");
        var process = startPty(
                input.toString(),
                transcript,
                true,
                Map.of("SSH_CONNECTION", "test"),
                "--timings",
                "--width=" + width,
                "--image-dim=false"
        );
        try {
            await(process, transcript, "timing first-frame:");
            assertTrue(screen(Files.readString(transcript)).contains("FIRST_TEXT"));
            process.getOutputStream().write("\u001B_Gi=2147483000;OK\u001B\\".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "timing first-image:");
            awaitScreen(process, transcript, "AFTER_BADGES");
            String output = Files.readString(transcript);
            assertTrue(output.indexOf("timing first-frame:") < output.indexOf("a=t,t=d,f=100"));
            var position = Pattern.compile("\u001B\\[(\\d+);(\\d+)H\u001B_Ga=p").matcher(output);
            assertTrue(position.find(), "SVG badge placement");
            int firstRow = Integer.parseInt(position.group(1));
            int firstColumn = Integer.parseInt(position.group(2));
            String coordinates = position.group(2) + ";" + position.group(1);
            assertTrue(position.find(), "Second SVG badge placement");
            assertEquals(
                    sameRow,
                    firstRow == Integer.parseInt(position.group(1)),
                    "Adjacent badges share a row when they fit"
            );
            if (sameRow) {
                assertTrue(Integer.parseInt(position.group(2)) > firstColumn);
            }
            int beforeResize = Files.readString(transcript).length();
            process.getOutputStream().write("\u001B[6;32;16t".getBytes());
            process.getOutputStream().flush();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (!Files.readString(transcript).substring(beforeResize).contains(",c=12")) {
                    Thread.sleep(10);
                }
            });
            process
                .getOutputStream()
                .write(("\u001B[C".repeat(8) + "\u001B[<0;" + coordinates + "M\u001B[<0;" + coordinates + "m")
                    .getBytes()
                );
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "SSH:");
            assertTrue(Files.readString(transcript).contains("https://example.org/build"));
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void handlesThemeNotificationsAndKittyImages() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")));
        var image = new BufferedImage(80, 32, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", directory.resolve("picture.png").toFile());
        Path document = directory.resolve("image.adoc");
        Files.writeString(document, "= Image\n\nimage::picture.png[]\n");
        Path transcript = directory.resolve("graphics-transcript");
        var process = startPty(document.toString(), transcript, true, "--page-background=true", "--progress=always");
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                await(process, transcript, "quit");
                process
                    .getOutputStream()
                    .write(("\u001B[?2031;2$y\u001B[?997;1n\u001B]11;rgb:1010/1010/1010\u001B\\"
                            + "\u001B_Gi=2147483000;OK\u001B\\")
                        .getBytes()
                    );
                process.getOutputStream().flush();
                await(process, transcript, "a=p,i=");
                await(process, transcript, "\u001B[?1003h");
                await(process, transcript, "\u001B]9;4;1;100\u0007");
                String dimmed = Files.readString(transcript);
                int uploads = dimmed.split("a=t,t=d,f=100", -1).length;
                var position = Pattern.compile("\u001B\\[(\\d+);(\\d+)H\u001B_Ga=p").matcher(dimmed);
                assertTrue(position.find(), "Image placement coordinates");
                process.getOutputStream().write(("\u001B[<35;" + position.group(2) + ";" + position.group(1) + "M")
                    .getBytes()
                );
                process.getOutputStream().flush();
                long hoverDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (Files.readString(transcript).split("a=t,t=d,f=100", -1).length == uploads
                        && System.nanoTime() < hoverDeadline) {
                    Thread.sleep(20);
                }
                assertTrue(
                        Files.readString(transcript).split("a=t,t=d,f=100", -1).length > uploads,
                        "Hover uploads the original image once"
                );
                process.getOutputStream().write("\u001B[?997;2n\u001B]11;rgb:ffff/ffff/ffff\u001B\\".getBytes());
                process.getOutputStream().flush();
                await(process, transcript, "255;255;255");
                process.getOutputStream().write('q');
                process.getOutputStream().flush();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, process.exitValue(), Files.readString(transcript));
                assertTrue(Files.readString(transcript).contains("\u001B]9;4;0\u0007"));
                assertTrue(Files.readString(transcript).contains("\u001B[?2031l"), "Restore theme notification mode");
                assertTrue(Files.readString(transcript).contains("a=d,d=I"), "Remove application-owned images");
            });
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private Process startPty(String document, Path transcript, boolean images, String... options) throws IOException {
        return startPty(document, transcript, images, Map.of(), options);
    }

    private Process startPty(
            String document,
            Path transcript,
            boolean images,
            Map<String, String> environment,
            String... options
    ) throws IOException {
        String invocation = "stty cols 80 rows 24 && exec "
                + quote(java)
                + " -jar "
                + quote(System.getProperty("glow.jar"))
                + " --images="
                + (images ? "auto" : "never")
                + " "
                + Arrays.stream(options).map(PackagedCliTest::quote).collect(Collectors.joining(" "))
                + " "
                + quote(document);
        List<String> command = System.getProperty("os.name").startsWith("Mac")
                ? List.of("/usr/bin/script", "-q", "/dev/null", "/bin/sh", "-c", invocation)
                : List.of("/usr/bin/script", "-q", "-e", "-c", invocation, "/dev/null");
        var builder = new ProcessBuilder(command).redirectOutput(transcript.toFile()).redirectErrorStream(true);
        builder.environment().put("TERM", "xterm-256color");
        builder.environment().put("COLORTERM", "truecolor");
        builder.environment().remove("NO_COLOR");
        builder.environment().putAll(environment);
        return builder.start();
    }

    @Test
    void mouseHoverHighlightsLinksAndClickReleaseInvokesTheOpener() throws Exception {
        Path document = directory.resolve("mouse-link.adoc");
        Files.writeString(document, "https://example.org/reference[LINK_LABEL]\n");
        Path transcript = directory.resolve("mouse-link-transcript");
        // SSH mode exercises real click dispatch without opening a browser during tests.
        var process = startPty(
                document.toString(),
                transcript,
                false,
                Map.of("SSH_CONNECTION", "test"),
                "--width=20",
                "--center=true",
                "--color=always"
        );
        try {
            await(process, transcript, "quit");
            await(process, transcript, "\u001B[?1003h");
            int beforeHover = Files.readString(transcript).length();
            process.getOutputStream().write("\u001B[<35;30;1M".getBytes());
            process.getOutputStream().flush();
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (!Files.readString(transcript).substring(beforeHover).contains("48;2;121;192;255")) {
                    Thread.sleep(10);
                }
            });
            assertTrue(Files.readString(transcript).substring(beforeHover).contains("https://example.org/reference"));
            process.getOutputStream().write("\u001B[<0;30;1M".getBytes());
            process.getOutputStream().flush();
            Thread.sleep(100);
            assertFalse(Files.readString(transcript).contains("SSH:"), "Press alone must not open links");
            process.getOutputStream().write("\u001B[<32;31;1M\u001B[<0;30;1m".getBytes());
            process.getOutputStream().flush();
            Thread.sleep(100);
            assertFalse(Files.readString(transcript).contains("SSH:"), "Dragging must cancel link activation");
            process.getOutputStream().write("\u001B[<0;30;1M\u001B[<0;30;1m".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "SSH:");
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @TableTest("""
        colour
        always
        never
        """)
    void pagerPreservesHyperlinksWithMouseCaptureAndSearch(String colour) throws Exception {
        Path document = directory.resolve("hyperlink.adoc");
        Files.writeString(document, "https://example.org/reference[LINK_LABEL across a wrapped line]\n");
        Path transcript = directory.resolve("hyperlink-transcript");
        var process =
                startPty(document.toString(), transcript, false, "--color=" + colour, "--width=20", "--progress=never");
        try {
            await(process, transcript, "https://example.org/reference");
            assertTrue(Files.readString(transcript).contains("\u001B]8;"));
            assertTrue(Files.readString(transcript).contains("\u001B[?1006h"));
            process.getOutputStream().write("/LINK_LABEL".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "1/1 matches");
            process.getOutputStream().write(3);
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
            assertFalse(Files.readString(transcript).contains("\u001B]9;4;"));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void directTtyOutputDetectsWidthAndHonoursCentering() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")));
        Path document = directory.resolve("direct.adoc");
        Files.writeString(document, "DIRECT_TTY_CONTENT\n");
        Path transcript = directory.resolve("direct-transcript");
        var process = startPty(
                document.toString(),
                transcript,
                false,
                "--no-pager",
                "--width=30",
                "--center=true",
                "--color=never"
        );
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
            assertTrue(Files.readString(transcript).contains("DIRECT_TTY_CONTENT"));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void foldsDetailsAndPaintsStatusInPseudoTerminal() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")));
        Path document = directory.resolve("details.adoc");
        Files.writeString(document, ".More\n[%collapsible]\n====\nFOLDED_CONTENT\n====\n");
        Path transcript = directory.resolve("fold-transcript");
        var process = startPty(document.toString(), transcript, false);
        try {
            await(process, transcript, "quit");
            assertFalse(Files.readString(transcript).contains("FOLDED_CONTENT"));
            process.getOutputStream().write("\t\r".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "FOLDED_CONTENT");
            assertTrue(Files.readString(transcript).contains("48;"), "Status row must have a background");
            process.getOutputStream().write("q".getBytes());
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void installedLauncherIsQuietAndRichOptionsOverrideConfiguration() throws Exception {
        Path config = directory.resolve("rich.toml");
        Files.writeString(config, "shortcodes = 'hide'\nicons = 'unicode'\ncenter = true\n");
        Path errors = directory.resolve("launcher-errors");
        var builder = new ProcessBuilder(
                System.getProperty("glow.executable"),
                "--no-pager",
                "--color=never",
                "--config=" + config,
                "--shortcodes=show",
                "--icons=ascii",
                "--center=false",
                "--table-stripes=false",
                "-"
        );
        selectTestJava(builder);
        var process = builder.redirectError(errors.toFile()).start();
        process.getOutputStream().write("{{< tag >}} visible\n\n[%collapsible]\n====\nExpanded\n====\n".getBytes());
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), Files.readString(errors));
        assertTrue(output.contains("{{< tag >}} visible"), output);
        assertTrue(output.contains("Expanded"), output);
        assertFalse(Files.readString(errors).contains("sun.misc.Unsafe"), Files.readString(errors));
    }

    private void selectTestJava(ProcessBuilder builder) {
        builder.environment().put("PATH", Path.of(System.getProperty("java.home"), "bin") + ":/usr/bin:/bin");
        builder.environment().put("SDKMAN_CANDIDATES_DIR", directory.resolve("unused-sdkman").toString());
    }

    private static void await(Process process, Path transcript, String text) throws Exception {
        await(process, transcript, text, 1);
    }

    private static void await(Process process, Path transcript, String text, int occurrences) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (Files.exists(transcript)
                    && Pattern
                .compile(Pattern.quote(text))
                .matcher(Files.readString(transcript))
                .results()
                .limit(occurrences)
                .count() >= occurrences) {
                return;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(50);
        }
        fail("Missing " + text + ": " + Files.readString(transcript));
    }

    @Test
    void mouseClicksWrappedCenteredHeadersAndWheelScrolls() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")));
        Path document = directory.resolve("mouse.adoc");
        String title = "A long disclosure title that wraps";
        Files.writeString(
                document,
                "."
                + title
                + "\n[%collapsible]\n====\nOPEN_CLICK\n====\n\n"
                + IntStream.range(0, 60).mapToObj(i -> "ROW_" + i + "\n\n").collect(Collectors.joining())
        );
        Path transcript = directory.resolve("mouse-transcript");
        var process = startPty(document.toString(), transcript, false, "--width=20", "--center=true", "--icons=ascii");
        try {
            await(process, transcript, "quit");
            assertFalse(Files.readString(transcript).contains("OPEN_CLICK"));
            var closedPosition = Pattern.compile(" 1/(\\d+) ").matcher(screen(Files.readString(transcript)));
            assertTrue(closedPosition.find());
            String closedTotal = closedPosition.group(1);
            // Click the second wrapped header row, then release at the same position.
            process.getOutputStream().write("\u001B[<0;40;2M\u001B[<0;40;2m".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "OPEN_CLICK");
            process.getOutputStream().write("\u001B[<65;40;10M".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 4/");
            process.getOutputStream().write("\u001B[<64;40;10M".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 1/");
            assertTrue(screen(Files.readString(transcript)).contains("OPEN_CLICK"), "Release must not close the block");
            process.getOutputStream().write("\u001B[?997;2n".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "36;41;47");
            assertTrue(
                    screen(Files.readString(transcript)).contains("OPEN_CLICK"),
                    "Theme changes must retain folding state"
            );
            process.getOutputStream().write('j');
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 2/");
            process.getOutputStream().write("\u001B[<0;40;1M\u001B[<0;40;1m".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 2/" + closedTotal + " ");
            assertTrue(
                    screen(Files.readString(transcript)).contains(" 2/"),
                    "The wrapped title must not move on folding"
            );
            assertFalse(screen(Files.readString(transcript)).contains("OPEN_CLICK"));
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(transcript));
            String output = Files.readString(transcript);
            assertTrue(output.contains("\u001B[?1006h"), output);
            assertTrue(output.contains("\u001B[?1006l"), output);
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void mouseCanBeDisabledWithoutDisablingKeyboardFolding() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")));
        Path document = directory.resolve("no-mouse.adoc");
        Files.writeString(document, ".More\n[%collapsible]\n====\nKEYBOARD_OPEN_MARKER\n====\n");
        Path transcript = directory.resolve("no-mouse-transcript");
        var process = startPty(document.toString(), transcript, false, "--mouse=false");
        try {
            await(process, transcript, "quit");
            assertFalse(Files.readString(transcript).contains("\u001B[?1006h"));
            process.getOutputStream().write("\t\r".getBytes());
            process.getOutputStream().flush();
            await(process, transcript, "KEYBOARD_OPEN_MARKER");
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            assertTrue(Files.readString(transcript).contains("KEYBOARD_OPEN_MARKER"));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }

    @Test
    void foldingNearDocumentEndKeepsTheTitleAtTheSameScreenRow() throws Exception {
        Path document = directory.resolve("stable-title.adoc");
        Files.writeString(
                document,
                        IntStream.range(0, 5).mapToObj(i -> "Preface " + i + "\n\n").collect(Collectors.joining())
                + ".Stable title\n[%collapsible%open]\n====\n"
                + IntStream.range(0, 40).mapToObj(i -> "Body " + i + "\n\n").collect(Collectors.joining())
                + "====\n"
        );
        Path transcript = directory.resolve("stable-title-transcript");
        var process = startPty(document.toString(), transcript, false, "--icons=ascii");
        try {
            await(process, transcript, "quit");
            process.getOutputStream().write("jjj\t".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, " 4/");
            String[] beforeRows = screen(Files.readString(transcript)).split("\n");
            int titleRow = IntStream
                .range(0, beforeRows.length)
                .filter(row -> beforeRows[row].contains("Stable title"))
                .findFirst()
                .orElseThrow();
            process.getOutputStream().write('\r');
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "[+] Stable title");
            assertTrue(screen(Files.readString(transcript)).split("\n")[titleRow].contains("Stable title"));
            assertTrue(screen(Files.readString(transcript)).contains(" 4/"));
            process.getOutputStream().write('\r');
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "[-] Stable title");
            assertTrue(screen(Files.readString(transcript)).split("\n")[titleRow].contains("Stable title"));
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void switchesBetweenMouseControlsAndNativeSelectionWithoutRestarting() throws Exception {
        Path document = directory.resolve("mouse-switch.adoc");
        Files.writeString(document, ".More\n[%collapsible]\n====\nSELECT_MODE\n====\n");
        Path transcript = directory.resolve("mouse-switch-transcript");
        var process = startPty(document.toString(), transcript, false, "--mouse=false");
        try {
            await(process, transcript, "m mouse");
            assertFalse(Files.readString(transcript).contains("\u001B[?1006h"));
            process.getOutputStream().write('m');
            process.getOutputStream().flush();
            await(process, transcript, "\u001B[?1006h");
            awaitScreen(process, transcript, "m select");
            process.getOutputStream().write("\u001B[<0;8;1M\u001B[<0;8;1m".getBytes());
            process.getOutputStream().flush();
            awaitScreen(process, transcript, "SELECT_MODE");
            process.getOutputStream().write('m');
            process.getOutputStream().flush();
            await(process, transcript, "\u001B[?1006l");
            awaitScreen(process, transcript, "m mouse");
            process.getOutputStream().write('q');
            process.getOutputStream().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Test
    void presentationArgumentsOverrideConfiguration() throws Exception {
        Path config = directory.resolve("presentation.toml");
        Files.writeString(
                config,
                "page-background = true\n"
                + "mouse = true\n"
                + "table-frame = 'all'\n"
                + "table-grid = 'all'\n"
                + "table-section-borders = 'heavy'\n"
        );
        var process = new ProcessBuilder(
                java,
                "-jar",
                System.getProperty("glow.jar"),
                "--no-pager",
                "--color=always",
                "--config=" + config,
                "--page-background=false",
                "--mouse=false",
                "--table-frame=none",
                "--table-grid=none",
                "--table-section-borders=thin",
                "--icons=ascii",
                "-"
        )
            .redirectError(directory.resolve("presentation-errors").toFile())
            .start();
        process.getOutputStream().write("ordinary\n\n|===\n|a |b\n|c |d\n|===\n".getBytes());
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertTrue(output.contains("\u001B[0mordinary\u001B[0m"), output);
        String plain = output.replaceAll("\u001B\\[[0-9;]*m", "");
        assertFalse(plain.contains("|") || plain.contains("+") || plain.contains("="), plain);
    }

    private static void awaitScreen(Process process, Path transcript, String text) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (Files.exists(transcript) && screen(Files.readString(transcript)).contains(text)) {
                return;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(50);
        }
        fail(
                "Missing screen text "
                + text
                + ": "
                + screen(Files.readString(transcript))
                + "\nTranscript: "
                + Files.readString(transcript)
        );
    }

    // Reconstruct ASCII fixtures because a terminal redraw can update just one digit.
    private static String screen(String output) {
        char[][] cells = new char[24][80];
        for (char[] row : cells) {
            Arrays.fill(row, ' ');
        }
        int x = 0, y = 0;
        for (int i = 0; i < output.length(); ) {
            char ch = output.charAt(i++);
            if (ch == 27 && i < output.length()) {
                char next = output.charAt(i++);
                if (next == '[') {
                    int start = i;
                    while (i < output.length() && !(output.charAt(i) >= '@' && output.charAt(i) <= '~')) {
                        i++;
                    }
                    if (i == output.length()) {
                        break;
                    }
                    String parameters = output.substring(start, i);
                    char command = output.charAt(i++);
                    if (command == 'H' || command == 'f') {
                        String[] position = parameters.split(";", -1);
                        y = Math.clamp(
                                position.length > 0 && !position[0].isEmpty() ? Integer.parseInt(position[0]) - 1 : 0,
                                0,
                                23
                        );
                        x = Math.clamp(
                                position.length > 1 && !position[1].isEmpty() ? Integer.parseInt(position[1]) - 1 : 0,
                                0,
                                79
                        );
                    } else if (command == 'J' && parameters.equals("2")
                            || command == 'h' && parameters.equals("?1049")) {
                        for (char[] row : cells) {
                            Arrays.fill(row, ' ');
                        }
                        if (command == 'h') {
                            x = 0;
                            y = 0;
                        }
                    } else if (command == 'K') {
                        Arrays.fill(cells[y], x, 80, ' ');
                    }
                } else if (next == ']' || next == '_') {
                    while (i < output.length()) {
                        char value = output.charAt(i++);
                        if (value == 7) {
                            break;
                        }
                        if (value == 27 && i < output.length() && output.charAt(i) == '\\') {
                            i++;
                            break;
                        }
                    }
                }
                continue;
            }
            if (ch == '\r') {
                x = 0;
            } else if (ch == '\n') {
                y = Math.min(23, y + 1);
            } else if (ch >= ' ' && x < 80) {
                cells[y][x++] = ch;
            }
        }
        return Arrays.stream(cells).map(String::new).collect(Collectors.joining("\n"));
    }
}

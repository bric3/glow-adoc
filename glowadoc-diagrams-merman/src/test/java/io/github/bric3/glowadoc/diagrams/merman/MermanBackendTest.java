// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams.merman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tabletest.junit.TableTest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

class MermanBackendTest {
    @TempDir
    Path directory;

    @Test
    void keepsSourceWhenMermanIsUnavailable() throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer =
                new DiagramRenderer(Map.of("mermaid", new MermanBackend("/nonexistent/glow-merman")), diagnostics::add)) {
            assertTrue(renderer.render(diagram(), Appearance.DARK).isEmpty());
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().contains("showing source"));
        }
    }

    @Test
    void removesInputWhenTheCommandCannotStart() throws Exception {
        try (var backend = new MermanBackend("/nonexistent/glow-merman")) {
            assertThrows(IOException.class, () -> backend.render(
                    "flowchart LR; A --> B",
                    directory.resolve("image.png"),
                    Appearance.LIGHT
            ));
            try (var files = Files.list(directory)) {
                assertEquals(0, files.count());
            }
        }
    }

    @Test
    void rendersInBackgroundRetainsThePreviousThemeAndCancelsOnClose() throws Exception {
        ImageIO.write(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
                "png",
                directory.resolve("image.png").toFile()
        );
        Files.createFile(directory.resolve("default.ready"));
        Block block = diagram();
        long pid;
        try (var renderer =
                new DiagramRenderer(Map.of("mermaid", new MermanBackend(command("render"))), message -> fail(message))) {
            Path light = renderer.render(block, Appearance.LIGHT).orElseThrow();
            assertNotNull(ImageIO.read(light.toFile()));
            assertEquals(
                    light,
                    assertTimeoutPreemptively(Duration.ofSeconds(1), () -> renderer.request(block, Appearance.DARK)).orElseThrow()
            );
            awaitFile(directory.resolve("dark.started"));
            pid = Long.parseLong(Files.readString(directory.resolve("dark.started")));
            assertEquals(light, renderer.cached(block, Appearance.DARK).orElseThrow());
            assertFalse(renderer.takeDirty());
        }
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                Thread.sleep(10);
            }
        });
    }

    @TableTest("""
        Mode
        failure
        empty
        output-limit
        diagnostic-limit
        """)
    void keepsSourceForFailedOrExcessiveOutput(String mode) throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer = new DiagramRenderer(Map.of("mermaid", new MermanBackend(command(mode))), diagnostics::add)) {
            assertTrue(renderer.render(diagram(), Appearance.LIGHT).isEmpty());
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().contains("showing source"));
        }
    }

    @Test
    void rendersWithInstalledMermanWhenRequested() throws Exception {
        String command = System.getenv("GLOW_TEST_MERMAN");
        org.junit.jupiter.api.Assumptions.assumeTrue(command != null);
        try (var renderer = new DiagramRenderer(Map.of("mermaid", new MermanBackend(command)), message -> fail(message))) {
            assertNotNull(ImageIO.read(renderer.render(diagram(), Appearance.DARK).orElseThrow().toFile()));
        }
    }

    private String command(String mode) throws Exception {
        Path command = directory.resolve("merman");
        String java = Path.of(System.getProperty("java.home"), "bin/java").toString();
        Path classes = Path.of(Merman.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.writeString(
                command,
                "#!/bin/sh\nexec "
                + quote(java)
                + " -cp "
                + quote(classes.toString())
                + " "
                + quote(Merman.class.getName())
                + " "
                + quote(directory.toString())
                + " "
                + quote(mode)
                + " \"$@\"\n"
        );
        assertTrue(command.toFile().setExecutable(true));
        return command.toString();
    }

    private static Block diagram() {
        return new Block(Block.Kind.DIAGRAM, "", List.of(new Span("flowchart LR\n A --> B")), List.of(), "mermaid", "");
    }

    private static void awaitFile(Path path) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!Files.exists(path) || Files.size(path) == 0) {
                Thread.sleep(10);
            }
        });
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static class Merman {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            String mode = args[1];
            if (mode.equals("failure")) {
                System.exit(3);
            }
            if (mode.equals("empty")) {
                return;
            }
            if (mode.endsWith("limit")) {
                var output = mode.equals("output-limit") ? System.out : System.err;
                byte[] bytes = new byte[8192];
                for (int i = 0; i < 1300; i++) {
                    output.write(bytes);
                }
                return;
            }
            var arguments = List.of(args);
            if (!arguments.containsAll(List.of("--resource-profile", "constrained", "--operation-timeout-ms", "14000"))) {
                throw new IllegalArgumentException("Merman resource limits missing");
            }
            String theme = arguments.get(arguments.indexOf("--theme") + 1);
            Files.writeString(directory.resolve(theme + ".started"), Long.toString(ProcessHandle.current().pid()));
            while (!Files.exists(directory.resolve(theme + ".ready"))) {
                Thread.sleep(10);
            }
            Files.copy(directory.resolve("image.png"), System.out);
        }
    }
}

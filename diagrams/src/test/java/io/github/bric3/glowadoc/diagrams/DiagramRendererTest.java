// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

class DiagramRendererTest {
    @TempDir
    Path directory;

    @Test
    void resolvesOnlyImagesWithinTheDocumentOrExplicitImageRoots() throws Exception {
        Path posts = Files.createDirectory(directory.resolve("posts"));
        Path assets = Files.createDirectory(directory.resolve("assets"));
        Path picture = Files.writeString(assets.resolve("picture.png"), "picture");
        Path secret = Files.writeString(directory.resolve("secret.png"), "outside");
        Files.createSymbolicLink(assets.resolve("escape.png"), secret);
        var restricted = new Document(List.of(), posts);
        assertTrue(DiagramRenderer.localImage(restricted, picture.toString()).isEmpty());
        var allowed = new Document(List.of(), posts, List.of(assets));
        assertEquals(picture.toRealPath(), DiagramRenderer.localImage(allowed, "picture.png").orElseThrow());
        assertEquals(picture.toRealPath(), DiagramRenderer.localImage(allowed, picture.toString()).orElseThrow());
        assertTrue(DiagramRenderer.localImage(allowed, "../secret.png").isEmpty());
        assertTrue(DiagramRenderer.localImage(allowed, "escape.png").isEmpty());
        assertTrue(DiagramRenderer.localImage(allowed, "https://example.org/picture.png").isEmpty());
    }

    private static Block diagram(String language, String source) {
        return new Block(Block.Kind.DIAGRAM, "", List.of(new Span(source)), List.of(), language, "");
    }

    @Test
    void rendersPlantumlAndKeepsMissingMermanSource() throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer = new DiagramRenderer("/nonexistent/glow-merman", diagnostics::add)) {
            var png = renderer.render(diagram("plantuml", "Alice -> Bob: Hello"), Appearance.LIGHT).orElseThrow();
            assertNotNull(ImageIO.read(png.toFile()));
            assertTrue(renderer.render(diagram("mermaid", "flowchart LR; A --> B"), Appearance.DARK).isEmpty());
            assertEquals(1, diagnostics.size());
            assertTrue(renderer
                .render(diagram("plantuml", "!include https://example.org/test"), Appearance.DARK)
                .isEmpty()
            );
        }
    }

    @Test
    void rendersInBackgroundRetainsThePreviousThemeAndCancelsOnClose() throws Exception {
        Path png = directory.resolve("image.png");
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
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
                + " \"$@\"\n"
        );
        assertTrue(command.toFile().setExecutable(true));
        Files.createFile(directory.resolve("default.ready"));
        Block block = diagram("mermaid", "flowchart LR; A --> B");
        long pid;
        try (var renderer = new DiagramRenderer(command.toString(), message -> fail(message))) {
            Path light = renderer.render(block, Appearance.LIGHT).orElseThrow();
            renderer.takeDirty();
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

    @Test
    void reportsBackgroundFailuresThroughThePagerQueue() throws Exception {
        try (var renderer = new DiagramRenderer("/nonexistent/glow-merman", message -> fail(message))) {
            Block block = diagram("mermaid", "flowchart LR; A --> B");
            renderer.request(block, Appearance.DARK);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (!renderer.takeDirty()) {
                    Thread.sleep(10);
                }
            });
            assertTrue(renderer.cached(block, Appearance.DARK).isEmpty());
            assertEquals(1, renderer.takeDiagnostics().size());
            assertTrue(renderer.takeDiagnostics().isEmpty());
        }
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
            String theme = List.of(args).get(List.of(args).indexOf("--theme") + 1);
            Files.writeString(directory.resolve(theme + ".started"), Long.toString(ProcessHandle.current().pid()));
            while (!Files.exists(directory.resolve(theme + ".ready"))) {
                Thread.sleep(10);
            }
            Files.copy(directory.resolve("image.png"), System.out);
        }
    }

    @Test
    void rendersWithInstalledMermanWhenRequested() throws Exception {
        String command = System.getenv("GLOW_TEST_MERMAN");
        org.junit.jupiter.api.Assumptions.assumeTrue(command != null);
        try (var renderer = new DiagramRenderer(command, message -> fail(message))) {
            assertNotNull(ImageIO.read(renderer
                .render(diagram("mermaid", "flowchart LR\n A --> B"), Appearance.DARK)
                .orElseThrow()
                .toFile()
            ));
        }
    }
}

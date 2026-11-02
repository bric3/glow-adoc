// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Test
    void rejectsOversizedSourcesAndUnsupportedLanguages() throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer = new DiagramRenderer(Map.of("test", DiagramRendererTest::image), diagnostics::add)) {
            assertTrue(renderer.render(diagram("test", "x".repeat(100_001)), Appearance.LIGHT).isEmpty());
            assertTrue(renderer.render(diagram("unsupported", "source"), Appearance.LIGHT).isEmpty());
            assertEquals(2, diagnostics.size());
            assertTrue(diagnostics.get(0).contains("source exceeds"));
            assertTrue(diagnostics.get(1).contains("Unknown diagram language"));
        }
    }

    @Test
    void cachesImagesBySourceAndAppearanceAndRemovesThemOnClose() throws Exception {
        Block block = diagram("test", "source");
        Path light;
        Path dark;
        try (var renderer = new DiagramRenderer(Map.of("test", DiagramRendererTest::image), message -> fail(message))) {
            light = renderer.render(block, Appearance.LIGHT).orElseThrow();
            assertEquals(light, renderer.render(block, Appearance.LIGHT).orElseThrow());
            dark = renderer.render(block, Appearance.DARK).orElseThrow();
            assertNotEquals(light, dark);
            assertNotNull(ImageIO.read(light.toFile()));
            assertNotNull(ImageIO.read(dark.toFile()));
        }
        assertFalse(Files.exists(light));
        assertFalse(Files.exists(dark));
    }

    @Test
    void retainsThePreviousThemeWhileRenderingAndCancelsOnClose() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        DiagramBackend backend =
                (source, output, appearance) -> {
            if (appearance == Appearance.DARK) {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            }
            image(source, output, appearance);
        };
        Block block = diagram("test", "source");
        try (var renderer = new DiagramRenderer(Map.of("test", backend), message -> fail(message))) {
            Path light = renderer.render(block, Appearance.LIGHT).orElseThrow();
            assertEquals(
                    light,
                    assertTimeoutPreemptively(Duration.ofSeconds(1), () -> renderer.request(block, Appearance.DARK)).orElseThrow()
            );
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(light, renderer.cached(block, Appearance.DARK).orElseThrow());
            assertFalse(renderer.takeDirty());
        }
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }

    @Test
    void reportsBackgroundFailuresThroughThePagerQueue() throws Exception {
        DiagramBackend backend = (source, output, appearance) -> {
            throw new IOException("renderer unavailable");
        };
        try (var renderer = new DiagramRenderer(Map.of("test", backend), message -> fail(message))) {
            Block block = diagram("test", "source");
            renderer.request(block, Appearance.DARK);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (!renderer.takeDirty()) {
                    Thread.sleep(10);
                }
            });
            assertTrue(renderer.cached(block, Appearance.DARK).isEmpty());
            var diagnostics = renderer.takeDiagnostics();
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().contains("renderer unavailable"));
            assertTrue(renderer.takeDiagnostics().isEmpty());
        }
    }

    private static Block diagram(String language, String source) {
        return new Block(Block.Kind.DIAGRAM, "", List.of(new Span(source)), List.of(), language, "");
    }

    private static void image(String source, Path output, Appearance appearance) throws IOException {
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output.toFile());
    }
}

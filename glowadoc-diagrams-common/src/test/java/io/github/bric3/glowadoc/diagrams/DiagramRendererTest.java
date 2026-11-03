// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
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
            assertTrue(renderer.render(diagram("test", "x".repeat(100_001)), Appearance.LIGHT, 1.0).isEmpty());
            assertTrue(renderer.render(diagram("unsupported", "source"), Appearance.LIGHT, 1.0).isEmpty());
            assertEquals(2, diagnostics.size());
            assertTrue(diagnostics.get(0).contains("source exceeds"));
            assertTrue(diagnostics.get(1).contains("Unknown diagram language"));
        }
    }

    @Test
    void cachesImagesBySourceAppearanceAndDensityAndRemovesThemOnClose() throws Exception {
        Block block = diagram("test", "source");
        Path light;
        Path dark;
        Path retina;
        try (var renderer = new DiagramRenderer(Map.of("test", DiagramRendererTest::image), message -> fail(message))) {
            light = renderer.render(block, Appearance.LIGHT, 1.0).orElseThrow();
            assertEquals(light, renderer.render(block, Appearance.LIGHT, 1.0).orElseThrow());
            dark = renderer.render(block, Appearance.DARK, 1.0).orElseThrow();
            retina = renderer.render(block, Appearance.LIGHT, 2.0).orElseThrow();
            assertNotEquals(light, dark);
            assertNotEquals(light, retina);
            assertEquals(retina, renderer.cached(block, Appearance.LIGHT, 2.0).orElseThrow());
            assertEquals(4, ImageIO.read(retina.toFile()).getWidth());
            assertNotNull(ImageIO.read(light.toFile()));
            assertNotNull(ImageIO.read(dark.toFile()));
        }
        assertFalse(Files.exists(light));
        assertFalse(Files.exists(dark));
        assertFalse(Files.exists(retina));
    }

    @TableTest("""
        scale
        0.0
        -1.0
        NaN
        Infinity
        """)
    void rejectsInvalidDensity(double scale) throws Exception {
        try (var renderer = new DiagramRenderer(Map.of("test", DiagramRendererTest::image), message -> fail(message))) {
            Block block = diagram("test", "source");
            assertThrows(IllegalArgumentException.class, () -> renderer.render(block, Appearance.LIGHT, scale));
            assertThrows(IllegalArgumentException.class, () -> renderer.request(block, Appearance.LIGHT, scale));
            assertThrows(IllegalArgumentException.class, () -> renderer.cached(block, Appearance.LIGHT, scale));
        }
    }

    @Test
    void retainsThePreviousThemeWhileRenderingAndCancelsOnClose() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        DiagramBackend backend =
                (source, output, appearance, scale) -> {
            if (appearance == Appearance.DARK) {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            }
            image(source, output, appearance, scale);
        };
        Block block = diagram("test", "source");
        try (var renderer = new DiagramRenderer(Map.of("test", backend), message -> fail(message))) {
            Path light = renderer.render(block, Appearance.LIGHT, 2.0).orElseThrow();
            assertEquals(
                    light,
                    assertTimeoutPreemptively(Duration.ofSeconds(1), () -> renderer.request(block, Appearance.DARK, 2.0)).orElseThrow()
            );
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(light, renderer.cached(block, Appearance.DARK, 2.0).orElseThrow());
            assertTrue(renderer.cached(block, Appearance.DARK, 1.0).isEmpty());
            assertFalse(renderer.takeDirty());
        }
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }

    @Test
    void reportsBackgroundFailuresThroughThePagerQueue() throws Exception {
        DiagramBackend backend =
                (source, output, appearance, scale) -> {
            throw new IOException("renderer unavailable");
        };
        try (var renderer = new DiagramRenderer(Map.of("test", backend), message -> fail(message))) {
            Block block = diagram("test", "source");
            renderer.request(block, Appearance.DARK, 1.0);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (!renderer.takeDirty()) {
                    Thread.sleep(10);
                }
            });
            assertTrue(renderer.cached(block, Appearance.DARK, 1.0).isEmpty());
            var diagnostics = renderer.takeDiagnostics();
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().contains("renderer unavailable"));
            assertTrue(renderer.takeDiagnostics().isEmpty());
        }
    }

    private static Block diagram(String language, String source) {
        return new Block(Block.Kind.DIAGRAM, "", List.of(new Span(source)), List.of(), language, "");
    }

    private static void image(String source, Path output, Appearance appearance, double scale) throws IOException {
        ImageIO.write(
                new BufferedImage((int) (2 * scale), (int) (2 * scale), BufferedImage.TYPE_INT_RGB),
                "png",
                output.toFile()
        );
    }
}

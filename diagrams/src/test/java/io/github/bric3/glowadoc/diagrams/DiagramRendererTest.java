// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.diagrams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

class DiagramRendererTest {
    @TempDir Path directory;

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
        assertEquals(
                picture.toRealPath(),
                DiagramRenderer.localImage(allowed, "picture.png").orElseThrow());
        assertEquals(
                picture.toRealPath(),
                DiagramRenderer.localImage(allowed, picture.toString()).orElseThrow());
        assertTrue(DiagramRenderer.localImage(allowed, "../secret.png").isEmpty());
        assertTrue(DiagramRenderer.localImage(allowed, "escape.png").isEmpty());
        assertTrue(
                DiagramRenderer.localImage(allowed, "https://example.org/picture.png").isEmpty());
    }

    private static Block diagram(String language, String source) {
        return new Block(
                Block.Kind.DIAGRAM, "", List.of(new Span(source)), List.of(), language, "");
    }

    @Test
    void rendersPlantumlAndKeepsMissingMermanSource() throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer = new DiagramRenderer("/nonexistent/glow-merman", diagnostics::add)) {
            var png =
                    renderer.render(diagram("plantuml", "Alice -> Bob: Hello"), Appearance.LIGHT)
                            .orElseThrow();
            assertNotNull(ImageIO.read(png.toFile()));
            assertTrue(
                    renderer.render(diagram("mermaid", "flowchart LR; A --> B"), Appearance.DARK)
                            .isEmpty());
            assertEquals(1, diagnostics.size());
            assertTrue(
                    renderer.render(
                                    diagram("plantuml", "!include https://example.org/test"),
                                    Appearance.DARK)
                            .isEmpty());
        }
    }

    @Test
    void rendersWithInstalledMermanWhenRequested() throws Exception {
        String command = System.getenv("GLOW_TEST_MERMAN");
        org.junit.jupiter.api.Assumptions.assumeTrue(command != null);
        try (var renderer = new DiagramRenderer(command, message -> fail(message))) {
            assertNotNull(
                    ImageIO.read(
                            renderer.render(
                                            diagram("mermaid", "flowchart LR\n A --> B"),
                                            Appearance.DARK)
                                    .orElseThrow()
                                    .toFile()));
        }
    }
}

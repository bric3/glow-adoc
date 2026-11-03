// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams.ditaa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Span;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

class DitaaBackendTest {
    @TableTest(
            """
        Appearance | scale | width | height
        LIGHT      | 1.0   | 130   | 112
        DARK       | 1.0   | 130   | 112
        LIGHT      | 1.5   | 195   | 168
        DARK       | 2.0   | 260   | 224
        LIGHT      | 3.0   | 390   | 336
        """
    )
    void rendersDitaa(Appearance appearance, double scale, int width, int height) throws Exception {
        try (var renderer = new DiagramRenderer(Map.of("ditaa", new DitaaBackend()), message -> fail(message))) {
            var png =
                    renderer
                .render(diagram("+-------+\n| cGRE  |\n| Queue |\n+-------+"), appearance, scale)
                .orElseThrow();
            var image = ImageIO.read(png.toFile());
            assertNotNull(image);
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());
            assertTrue(Files.size(png) < 10_000_000);
        }
    }

    @Test
    void keepsSourceWhenImageDimensionsExceedLimits() throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer = new DiagramRenderer(Map.of("ditaa", new DitaaBackend()), diagnostics::add)) {
            assertTrue(renderer.render(diagram("x".repeat(4_000)), Appearance.LIGHT, 1.0).isEmpty());
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().contains("showing source"));
        }
    }

    private static Block diagram(String source) {
        return new Block(Block.Kind.DIAGRAM, "", List.of(new Span(source)), List.of(), "ditaa", "");
    }
}

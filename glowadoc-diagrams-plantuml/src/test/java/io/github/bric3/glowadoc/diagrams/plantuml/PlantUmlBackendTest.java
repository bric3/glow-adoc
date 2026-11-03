// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams.plantuml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Span;
import org.tabletest.junit.TableTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

class PlantUmlBackendTest {
    @TableTest("""
        Appearance
        LIGHT
        DARK
        """)
    void rendersPlantuml(Appearance appearance) throws Exception {
        try (var renderer = new DiagramRenderer(Map.of("plantuml", new PlantUmlBackend()), message -> fail(message))) {
            var png = renderer.render(diagram("Alice -> Bob: Hello"), appearance, 1.0).orElseThrow();
            assertNotNull(ImageIO.read(png.toFile()));
        }
    }

    @TableTest(
            """
        scale | documentScale
        1.5   | ''
        2.0   | ''
        3.0   | ''
        2.0   | 'scale 0.5'
        2.0   | 'scale 100 width'
        """
    )
    void rasterisesAtTheRequestedDensity(double scale, String documentScale) throws Exception {
        try (var renderer = new DiagramRenderer(Map.of("plantuml", new PlantUmlBackend()), message -> fail(message))) {
            Block block = diagram("@startuml\n" + documentScale + "\nAlice -> Bob: Hello\n@enduml");
            var normal = ImageIO.read(renderer.render(block, Appearance.LIGHT, 1.0).orElseThrow().toFile());
            var scaled = ImageIO.read(renderer.render(block, Appearance.LIGHT, scale).orElseThrow().toFile());
            assertEquals(normal.getWidth() * scale, scaled.getWidth(), 1.0);
            assertEquals(normal.getHeight() * scale, scaled.getHeight(), 1.0);
        }
    }

    @TableTest(
            """
        Source
        '!include https://example.org/test'
        '!import https://example.org/test'
        '!theme spacelab'
        '@startuml'
        """
    )
    void keepsSourceForUnsafeOrInvalidDiagrams(String source) throws Exception {
        var diagnostics = new ArrayList<String>();
        try (var renderer = new DiagramRenderer(Map.of("plantuml", new PlantUmlBackend()), diagnostics::add)) {
            assertTrue(renderer.render(diagram(source), Appearance.LIGHT, 1.0).isEmpty());
            assertEquals(1, diagnostics.size());
            assertTrue(diagnostics.getFirst().contains("showing source"));
        }
    }

    private static Block diagram(String source) {
        return new Block(Block.Kind.DIAGRAM, "", List.of(new Span(source)), List.of(), "plantuml", "");
    }
}

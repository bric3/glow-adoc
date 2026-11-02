// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.highlight.lumis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Span;
import org.junit.jupiter.api.Test;

class LumisHighlighterTest {
    @Test
    void preservesSourceAndProducesColours() {
        try (var highlighter = new LumisHighlighter()) {
            String source = "public class Hello { int n = 42; }";
            var spans = highlighter.highlight(source, "java", Appearance.DARK);
            assertEquals(source, spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining()));
            assertTrue(spans
                .stream()
                .anyMatch(s -> !s.style().foreground().isEmpty()));
            assertEquals(
                    "unknown",
                    highlighter.highlight("unknown", "no-such-language", Appearance.LIGHT).getFirst().text()
            );
        }
    }

    @Test
    void decodesOnlyStylingAndMakesControlsVisible() {
        var spans = LumisHighlighter.decode("\u001B[38;2;255;0;0mred\u001B[0m plain\u001B]52;data\u0007");
        assertEquals("#ff0000", spans.getFirst().style().foreground());
        assertFalse(spans.toString().contains("\u001B"));
    }
}

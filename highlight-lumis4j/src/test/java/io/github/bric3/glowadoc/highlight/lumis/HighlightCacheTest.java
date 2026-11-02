// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.highlight.lumis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class HighlightCacheTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsStylesAndValidatesEveryRenderingInput() {
        var cache = new HighlightCache(directory, "implementation");
        var spans = List.of(
                new Span("val ", new Style(true, true, true, true, "#aBcDeF", "#123456", "https://example.org", true)),
                new Span("é = 1;\n")
        );
        String source = "val é = 1;\n";
        cache.write(source, "java", Appearance.DARK, spans);
        assertEquals(spans, new HighlightCache(directory, "implementation").read(source, "java", Appearance.DARK));
        assertNull(cache.read(source, "java", Appearance.LIGHT));
        assertNull(cache.read(source, "kotlin", Appearance.DARK));
        assertNull(cache.read(source + " ", "java", Appearance.DARK));
        assertNull(new HighlightCache(directory, "updated").read(source, "java", Appearance.DARK));
    }

    @Test
    void rejectsSourceChangesTerminalControlsAndInvalidColours() {
        var cache = new HighlightCache(directory, "implementation");
        cache.write("source", "java", Appearance.DARK, List.of(new Span("altered")));
        assertNull(cache.read("source", "java", Appearance.DARK));
        for (String colour : List.of("#12345", "#1234567", "#Ｆfffff", "#g00000")) {
            cache.write("source", "java", Appearance.DARK, List.of(new Span("source", Style.PLAIN.colours(colour, ""))));
            assertNull(cache.read("source", "java", Appearance.DARK), colour);
        }
        cache.write("source", "java", Appearance.DARK, List.of(new Span("source", Style.PLAIN.colours("[31m", ""))));
        assertNull(cache.read("source", "java", Appearance.DARK));
        cache.write(
                "source",
                "java",
                Appearance.DARK,
                List.of(new Span("source", Style.PLAIN.linked("javascript:alert(1)")))
        );
        assertNull(cache.read("source", "java", Appearance.DARK));
    }
}

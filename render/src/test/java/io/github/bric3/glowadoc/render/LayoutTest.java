// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

class LayoutTest {
    @Test
    void wrapsWideTextAndRetainsUnwrappedSource() {
        var layout = new Layout((source, language, appearance) -> List.of(new Span(source)));
        var doc =
                new Document(
                        List.of(
                                Block.text(Block.Kind.PARAGRAPH, "日本語abc"),
                                Block.text(Block.Kind.CODE, "    long source line")),
                        Path.of("."));
        var lines = layout.render(doc, 4, Appearance.DARK);
        assertEquals("日本", lines.getFirst().text());
        assertTrue(lines.stream().anyMatch(l -> l.text().equals("    long source line")));
    }

    @Test
    void keepsWordsTogetherWhenTheyFit() {
        var layout = new Layout((source, language, appearance) -> List.of(new Span(source)));
        var doc =
                new Document(
                        List.of(Block.text(Block.Kind.PARAGRAPH, "one three four")), Path.of("."));
        var lines = layout.render(doc, 8, Appearance.DARK);
        assertEquals("one ", lines.getFirst().text());
        assertEquals("three ", lines.get(1).text());
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.pager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.render.RenderedLine;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class SearchTest {
    private final Search search = new Search();

    @Test
    void highlightsEveryLiteralMatchAcrossStylesAndPreservesTheSource() {
        var plain = Style.PLAIN;
        var italic = plain.italicised().linked("https://example.org");
        var spans = List.of(new Span("A [Ja", plain), new Span("Va] and [JAVA]", italic));
        var lines = List.of(new RenderedLine(spans, 0, null));
        search.update("[java]", lines, 0, 0);

        var parts = search.parts(0, spans);
        assertEquals(2, search.count());
        assertEquals("A [JaVa] and [JAVA]", text(parts));
        assertEquals("[JaVa][JAVA]", text(parts.stream().filter(Search.Part::matched).toList()));
        assertEquals("[JaVa]", text(parts.stream().filter(Search.Part::active).toList()));
        assertTrue(parts
            .stream()
            .anyMatch(part -> part.matched() && part.span().style().equals(italic)));
        assertTrue(parts
            .stream()
            .anyMatch(part -> !part.matched() && part.span().text().equals(" and ")));
    }

    @Test
    void selectsTheNearestLineWithTheOneAboveWinningTies() {
        var lines = lines("", "", "hit", "", "", "", "hit");
        search.update("hit", lines, 4, 0);
        assertEquals(2, search.active().line());
        search.update("hit", lines, 5, 0);
        assertEquals(6, search.active().line());
        search.update("hit", lines, 6, 0);
        assertEquals(6, search.active().line());
    }

    @Test
    void navigatesIndividualOccurrencesInBothDirectionsAndWraps() {
        var lines = lines("hit hit", "hit");
        search.update("hit", lines, 0, 2);
        assertEquals(0, search.active().start());
        search.next(1);
        assertEquals(4, search.active().start());
        assertEquals(2, search.ordinal());
        assertEquals(
                "hit",
                text(search.parts(0, lines.getFirst().spans()).stream().filter(Search.Part::active).toList())
        );
        search.next(1);
        assertEquals(1, search.active().line());
        search.next(1);
        assertEquals(1, search.ordinal());
        search.next(-1);
        assertEquals(3, search.ordinal());
        search.update("hit", lines, 0, 3);
        assertEquals(4, search.active().column());
    }

    @Test
    void includesOverlappingOccurrences() {
        var lines = lines("banana");
        search.update("ana", lines, 0, 0);
        assertEquals(2, search.count());
        var parts = search.parts(0, lines.getFirst().spans());
        assertEquals("anana", text(parts.stream().filter(Search.Part::matched).toList()));
        assertEquals("ana", text(parts.stream().filter(Search.Part::active).toList()));
        search.next(1);
        assertEquals(3, search.active().start());
    }

    @Test
    void preservesUnicodeOffsetsAndHighlightsWholeGraphemes() {
        var lines = lines("Ω 😀 é 👩‍💻");
        search.update("ω", lines, 0, 0);
        assertEquals(1, search.count());
        search.update("e", lines, 0, 0);
        assertEquals(
                "é",
                text(search.parts(0, lines.getFirst().spans()).stream().filter(Search.Part::matched).toList())
        );
        assertEquals(5, search.active().column());
        search.update("💻", lines, 0, 0);
        assertEquals(
                "👩‍💻",
                text(search.parts(0, lines.getFirst().spans()).stream().filter(Search.Part::matched).toList())
        );
        assertEquals(7, search.active().column());
        assertEquals(9, search.active().endColumn());
    }

    @Test
    void clearsStalePositionsAfterReflowAndEmptyOrUnmatchedQueries() {
        search.update("hit", lines("prefix hit"), 0, 0);
        assertEquals(7, search.active().column());
        var reflowed = lines("prefix", "hit");
        search.update(search.query(), reflowed, 0, 0);
        assertEquals(1, search.active().line());
        assertFalse(search.parts(0, reflowed.getFirst().spans()).getFirst().matched());
        search.update("missing", reflowed, 0, 0);
        assertEquals(0, search.count());
        assertNull(search.active());
        search.next(1);
        search.update("", reflowed, 0, 0);
        assertNull(search.active());
        assertFalse(search.parts(1, reflowed.get(1).spans()).getFirst().matched());
    }

    private static List<RenderedLine> lines(String... text) {
        return Arrays
            .stream(text)
            .map(line -> new RenderedLine(List.of(new Span(line)), 0, null))
            .toList();
    }

    private static String text(List<Search.Part> parts) {
        return parts
            .stream()
            .map(part -> part.span().text())
            .collect(Collectors.joining());
    }
}

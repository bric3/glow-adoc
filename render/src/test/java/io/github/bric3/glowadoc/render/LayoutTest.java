// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.tamboui.text.CharWidth;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

class LayoutTest {
    @Test
    void syntaxHighlightingPreservesPillColoursInSourceBlocks() {
        var syntaxStyle = Style.PLAIN.colours("#ff0000", "#111111");
        var highlighted = new Layout((text, language, appearance) -> text
            .codePoints()
            .mapToObj(cp -> new Span(Character.toString(cp), syntaxStyle))
            .toList());
        var source =
                new Block(Block.Kind.CODE, "", List.of(new Span("value "), Span.callout("12")), List.of(), "java", "");
        for (Appearance appearance : Appearance.values()) {
            var palette = Palette.forAppearance(appearance);
            var spans = highlighted
                .render(new Document(List.of(source), Path.of(".")), 40, appearance)
                .stream()
                .flatMap(line -> line.spans().stream())
                .toList();
            assertTrue(
                    spans
                        .stream()
                        .anyMatch(span -> span.text().contains("12")
                        && span.style().foreground().equals(palette.canvas())
                        && span.style().background().equals(palette.accent())),
                    spans.toString()
            );
            for (String cap : List.of("", "")) {
                assertTrue(
                        spans
                            .stream()
                            .anyMatch(span -> span.text().equals(cap)
                            && span.style().foreground().equals(palette.accent())
                            && span.style().background().equals(palette.surface())),
                        spans.toString()
                );
            }
        }
    }

    @Test
    void pillsUseRegularNumbersAndWrapTogether() {
        var spans = List.of(new Span("prefix "), Span.callout("123"), new Span(" rest"));
        var doc = new Document(List.of(new Block(Block.Kind.PARAGRAPH, "", spans, List.of(), "", "")), Path.of("."));
        var rows = layout.render(doc, 10, Appearance.DARK);
        assertTrue(rows
            .stream()
            .anyMatch(row -> row.text().contains(" 123 ")), rows.toString());
        assertTrue(rows
            .stream()
            .allMatch(row -> CharWidth.of(row.text()) <= 10));
        for (var appearance : Appearance.values()) {
            var palette = Palette.forAppearance(appearance);
            assertEquals(
                    5,
                    java.util.stream.Stream
                        .of("note", "tip", "important", "warning", "caution")
                        .map(palette::admonition)
                        .distinct()
                        .count()
            );
        }
    }

    @TableTest(
            """
        appearance | kind      | foreground
        LIGHT      | note      | #ffffff
        LIGHT      | tip       | #ffffff
        LIGHT      | important | #ffffff
        LIGHT      | warning   | #ffffff
        LIGHT      | caution   | #ffffff
        DARK       | note      | #000000
        DARK       | tip       | #000000
        DARK       | important | #000000
        DARK       | warning   | #000000
        DARK       | caution   | #000000
        """
    )
    void embedsAdmonitionLabelsWithPaddedContrastingBackgrounds(Appearance appearance, String kind, String foreground) {
        var palette = Palette.forAppearance(appearance);
        var lines = layout.render(
                new Document(List.of(admonition(kind, Block.text(Block.Kind.PARAGRAPH, "body"))), Path.of(".")),
                32,
                appearance
        );
        var header = lines.getFirst();
        var label =
                header
            .spans()
            .stream()
            .filter(span -> span.style().background().equals(palette.admonition(kind)))
            .toList();
        String icon = switch (kind) {
            case "tip" -> "";
            case "warning" -> "";
            case "caution" -> Character.toString(0xf0238);
            case "important" -> "";
            default -> "";
        };
        String title = icon + " " + kind.toUpperCase(Locale.ROOT);
        assertEquals(" " + title + " ", label.stream().map(Span::text).collect(Collectors.joining()));
        assertTrue(label
            .stream()
            .allMatch(span -> span.style().bold() && span.style().foreground().equals(foreground)));
        assertTrue(header.text().startsWith("┌── " + title + " "));
        assertEquals("body", lines.get(1).text().substring(2, 6));
        assertTrue(lines
            .stream()
            .filter(line -> !line.text().isEmpty())
            .allMatch(line -> CharWidth.of(line.text()) == 32)
        );
        assertEquals(
                Style.PLAIN.colours(palette.admonition(kind), palette.surface()),
                header.spans().getFirst().style()
        );
        assertEquals(header.spans().getFirst().style(), header.spans().getLast().style());
    }

    @Test
    void keepsNestedAdmonitionLabelsInsideTheirFrames() {
        Block nested = container(
                Block.Kind.QUOTE,
                admonition("warning", admonition("caution", Block.text(Block.Kind.PARAGRAPH, "nested body")))
        );
        var lines = render(nested, 44);
        var label = lines
            .stream()
            .filter(line -> line.text().contains("CAUTION"))
            .findFirst()
            .orElseThrow();
        assertTrue(label.text().startsWith("│ │ ┌── 󰈸 CAUTION "), label.text());
        assertTrue(label
            .spans()
            .stream()
            .anyMatch(span -> span.text().contains("CAUTION")
                    && span.style().background().equals("#d29922")
                    && span.style().foreground().equals("#000000"))
        );
        assertTrue(lines
            .stream()
            .allMatch(line -> CharWidth.of(line.text()) <= 44));
    }

    @TableTest("""
        width
        5
        7
        8
        12
        18
        """)
    void wrapsAsciiAdmonitionLabelsAndPreservesTheNarrowFallback(int width) {
        var ascii = new Layout((s, l, a) -> List.of(new Span(s)), new RenderOptions("ascii", false));
        var lines = ascii.render(
                new Document(List.of(admonition("important", Block.text(Block.Kind.PARAGRAPH, "body"))), Path.of(".")),
                width,
                Appearance.DARK
        );
        assertTrue(lines
            .stream()
            .allMatch(line -> CharWidth.of(line.text()) <= width), lines.toString());
        String title = lines
            .stream()
            .flatMap(line -> line.spans().stream())
            .filter(span -> span.style().background().equals("#bc8cff"))
            .map(Span::text)
            .collect(Collectors.joining())
            .replace(" ", "");
        assertEquals("!IMPORTANT", title);
        assertEquals(width >= 8, lines.getFirst().text().startsWith("+--"));
    }

    private static Block admonition(String kind, Block child) {
        return new Block(
                Block.Kind.ADMONITION,
                "",
                List.of(),
                List.of(child),
                "",
                "",
                new Block.Format("", kind, "", 1, 1, 0, false)
        );
    }

    @Test
    void rendersMatchingSourceAndListCalloutsWithNumberFallback() {
        var markers = List.of(
                new Span("one "),
                Span.callout("1"),
                new Span(" ten "),
                Span.callout("10"),
                new Span(" eleven "),
                Span.callout("11")
        );
        Block source = new Block(Block.Kind.CODE, "", markers, List.of(), "java", "");
        Block item = new Block(Block.Kind.ITEM, "", markers, List.of(), "", "");
        var doc = new Document(List.of(source, container(Block.Kind.LIST, item)), Path.of("."));
        String text = new Layout(
                (s, l, a) -> List.of(new Span(s)),
                new RenderOptions("nerd", true, false, "auto", "auto", "thin", "circle")
        )
            .render(doc, 60, Appearance.DARK)
            .stream()
            .map(RenderedLine::text)
            .collect(Collectors.joining("\n"));
        assertEquals(2, text.chars().filter(c -> c == '❶').count(), text);
        assertEquals(2, text.chars().filter(c -> c == '❿').count(), text);
        assertEquals(2, text.split("\\(11\\)", -1).length - 1, text);
        var ascii = new Layout((s, l, a) -> List.of(new Span(s)), new RenderOptions("ascii", false));
        String plain =
                ascii
            .render(doc, 60, Appearance.DARK)
            .stream()
            .map(RenderedLine::text)
            .collect(Collectors.joining("\n"));
        assertEquals(2, plain.split("\\(1\\)", -1).length - 1, plain);
        assertFalse(plain.contains("❶"));
    }

    @Test
    void sourceCaptionWrapsWithinItsTopBorder() {
        Block source = new Block(
                Block.Kind.CODE,
                "",
                List.of(new Span("code")),
                List.of(),
                "",
                "",
                new Block.Format("Enable GC logging programmatically", "", "", 1, 1, 0, false)
        );
        var lines = render(source, 24);
        assertTrue(lines.getFirst().text().startsWith("┌── Enable GC"), lines.getFirst().text());
        assertTrue(
                lines
                    .stream()
                    .filter(line -> !line.text().isEmpty())
                    .allMatch(line -> CharWidth.of(line.text()) == 24),
                lines.toString()
        );
        assertTrue(lines
            .stream()
            .anyMatch(line -> line.text().contains("programmatically")), lines.toString());
    }

    @Test
    void tableBordersStayTransparentWhileCellsRetainTheirBackgrounds() {
        var table = table(
                "all",
                "all",
                section("header", cell("a", 1, 1, "left")),
                container(Block.Kind.ROW, cell("b", 1, 1, "left"))
        );
        var spans = render(table, 20)
            .stream()
            .flatMap(line -> line.spans().stream())
            .toList();
        assertTrue(spans
            .stream()
            .filter(span -> span.text().matches(".*[│─┌┬┐├┼┤└┴┘].*"))
            .allMatch(span -> span.style().background().isEmpty())
        );
        assertTrue(spans
            .stream()
            .anyMatch(span -> span.text().equals("a") && !span.style().background().isEmpty()));
        assertTrue(spans
            .stream()
            .anyMatch(span -> span.text().equals("b") && !span.style().background().isEmpty()));
    }

    @Test
    void rendersItalicStyledCaptionsAndContinuousBlockBackgrounds() {
        Block code = new Block(
                Block.Kind.CODE,
                "",
                List.of(new Span("literal <code>")),
                List.of(),
                "",
                "",
                new Block.Format(
                        List.of(new Span("java", Style.PLAIN.coded()), new Span(" command")),
                        "",
                        "",
                        1,
                        1,
                        0,
                        false,
                        "all",
                        "all"
                )
        );
        var lines = render(code, 24);
        assertTrue(lines.getFirst().text().startsWith("┌── java command "), lines.getFirst().text());
        assertTrue(lines
            .getFirst()
            .spans()
            .stream()
            .filter(s -> s.text().contains("java") || s.text().contains("command"))
            .allMatch(s -> s.style().italic())
        );
        assertTrue(lines
            .getFirst()
            .spans()
            .stream()
            .anyMatch(s -> s.text().equals("java") && s.style().code()));
        String surface = Palette.forAppearance(Appearance.DARK).surface();
        for (var line : lines.subList(1, lines.size() - 1)) {
            assertTrue(line
                .spans()
                .stream()
                .allMatch(s -> s.style().background().equals(surface)), line.toString());
        }
        var paragraph = render(Block.text(Block.Kind.PARAGRAPH, "ordinary text"), 24);
        assertTrue(paragraph
            .getFirst()
            .spans()
            .stream()
            .allMatch(s -> s.style().background().isEmpty()));
        var filled = new Layout(
                (s, l, a) -> List.of(new Span(s)),
                new RenderOptions("nerd", true, true, "auto", "auto", "thin")
        )
            .render(
                    new Document(List.of(Block.text(Block.Kind.PARAGRAPH, "ordinary text")), Path.of(".")),
                    24,
                    Appearance.LIGHT
            );
        assertEquals(24, CharWidth.of(filled.getFirst().text()));
        assertTrue(filled
            .getFirst()
            .spans()
            .stream()
            .allMatch(s -> !s.style().background().isEmpty()));
    }

    private static Block table(String frame, String grid, Block... rows) {
        return new Block(
                Block.Kind.TABLE,
                "",
                List.of(),
                List.of(rows),
                "",
                "",
                new Block.Format(List.of(), "", "", 1, 1, 0, false, frame, grid)
        );
    }

    private static Block section(String variant, Block... cells) {
        return new Block(
                Block.Kind.ROW,
                "",
                List.of(),
                List.of(cells),
                "",
                "",
                new Block.Format("", variant, "", 1, 1, 0, false)
        );
    }

    @Test
    void respectsTableFramesGridsAndOverrides() {
        Block row = container(Block.Kind.ROW, cell("a", 1, 1, "left"), cell("b", 1, 1, "left"));
        var rowsOnly = render(table("none", "rows", row, row), 20);
        assertTrue(rowsOnly
            .stream()
            .anyMatch(l -> l.text().equals("─────────")));
        assertFalse(rowsOnly.stream().anyMatch(l -> l.text().contains("│")));
        var noGrid = render(table("all", "none", row, row), 20);
        assertEquals("┌───────┐", noGrid.getFirst().text());
        assertFalse(noGrid.stream().anyMatch(l -> l.text().contains("┼")));
        var override = new Layout(
                (s, l, a) -> List.of(new Span(s)),
                new RenderOptions("ascii", false, false, "none", "none", "thin")
        );
        var plain =
                override.render(new Document(List.of(table("all", "all", row, row)), Path.of(".")), 20, Appearance.DARK);
        assertTrue(plain
            .stream()
            .noneMatch(l -> l.text().contains("|") || l.text().contains("-")));
    }

    @Test
    void emphasisesSectionsAndDoesNotDrawRulesThroughSpanningCells() {
        Block table = table(
                "all",
                "all",
                section("header", cell("Name", 1, 1, "left"), cell("Value", 1, 1, "left")),
                container(Block.Kind.ROW, cell("spanning", 1, 2, "left"), cell("1", 1, 1, "left")),
                container(Block.Kind.ROW, cell("2", 1, 1, "left")),
                section("footer", cell("Total", 2, 1, "left"))
        );
        var heavy = new Layout(
                (s, l, a) -> List.of(new Span(s)),
                new RenderOptions("unicode", false, false, "auto", "auto", "heavy")
        );
        var lines = heavy.render(new Document(List.of(table), Path.of(".")), 30, Appearance.DARK);
        assertEquals(2, lines
            .stream()
            .filter(l -> l.text().contains("━"))
            .count());
        assertTrue(lines
            .stream()
            .anyMatch(l -> l.text().startsWith("│ ") && l.text().contains("─")));
        assertTrue(lines
            .stream()
            .filter(l -> !l.text().isEmpty())
            .allMatch(l -> CharWidth.of(l.text()) == CharWidth.of(lines.getFirst().text()))
        );
    }

    @Test
    void reservesFinalColumnAndCentersLimitedContent() {
        assertEquals(new ContentArea(40, 29), ContentArea.within(100, 40, true));
        assertEquals(new ContentArea(79, 0), ContentArea.within(80, 200, true));
        assertEquals(new ContentArea(79, 0), ContentArea.within(80, 0, false));
        assertEquals(new ContentArea(1, 0), ContentArea.within(1, 0, true));
    }

    private final Layout layout = new Layout((text, language, appearance) -> List.of(new Span(text)));

    private List<RenderedLine> render(Block block, int width) {
        return layout.render(new Document(List.of(block), Path.of(".")), width, Appearance.DARK);
    }

    private static Block container(Block.Kind kind, Block... children) {
        return new Block(kind, "", List.of(), List.of(children), "", "");
    }

    private static Block cell(String text, int columns, int rows, String align) {
        return new Block(
                Block.Kind.CELL,
                "",
                List.of(new Span(text)),
                List.of(),
                "",
                "",
                new Block.Format("", "", align, columns, rows, 0, false)
        );
    }

    @Test
    void wrapsWideTextAndRetainsUnwrappedSource() {
        assertEquals("日本", render(Block.text(Block.Kind.PARAGRAPH, "日本語abc"), 4).getFirst().text());
        assertTrue(render(Block.text(Block.Kind.CODE, "    long source line"), 12)
            .stream()
            .anyMatch(l -> l.text().contains("    long source line"))
        );
    }

    @Test
    void keepsWordsTogetherAcrossStyleBoundaries() {
        Block paragraph = new Block(
                Block.Kind.PARAGRAPH,
                "",
                List.of(new Span("one thr"), new Span("ee", Style.PLAIN.bolded()), new Span(" four")),
                List.of(),
                "",
                ""
        );
        var lines = render(paragraph, 8);
        assertEquals("one", lines.getFirst().text());
        assertEquals("three", lines.get(1).text());
        assertEquals("four", lines.get(2).text());
    }

    @Test
    void alignsAndWrapsTableCellsByTerminalWidth() {
        Block table = container(
                Block.Kind.TABLE,
                container(Block.Kind.ROW, cell("日本語", 1, 1, "left"), cell("hello world long", 1, 1, "right")),
                container(Block.Kind.ROW, cell("é", 1, 1, "left"), cell("42", 1, 1, "right"))
        );
        var lines = render(table, 24);
        int width = CharWidth.of(lines.getFirst().text());
        for (var line : lines) {
            if (!line.text().isEmpty()) {
                assertEquals(width, CharWidth.of(line.text()), line.text());
            }
        }
        assertTrue(width <= 24);
        assertTrue(lines.stream().anyMatch(l -> l.text().contains("42")));
        assertTrue(lines
            .stream()
            .flatMap(l -> l.spans().stream())
            .anyMatch(s -> !s.style().background().isEmpty()));
    }

    @Test
    void retainsColumnAndRowSpanningContent() {
        Block table = container(
                Block.Kind.TABLE,
                container(Block.Kind.ROW, cell("row span content", 1, 2, "left"), cell("a", 1, 1, "left")),
                container(Block.Kind.ROW, cell("b", 1, 1, "left")),
                container(Block.Kind.ROW, cell("spans columns", 2, 1, "center"))
        );
        String text = render(table, 25).stream().map(RenderedLine::text).collect(Collectors.joining("\n"));
        assertTrue(text.contains("spans columns"), text);
        assertTrue(text.contains("content"), text);
    }

    @Test
    void nestsQuoteRailsAndAccountsForTheirWidth() {
        var block = container(
                Block.Kind.QUOTE,
                container(Block.Kind.QUOTE, Block.text(Block.Kind.PARAGRAPH, "a long nested quote"))
        );
        var lines = render(block, 14);
        assertTrue(lines.getFirst().text().startsWith("│ │ "));
        assertTrue(lines.stream().allMatch(l -> CharWidth.of(l.text()) <= 14));
    }

    @Test
    void stylesInlineCodeWithoutUnderliningAndPreservesStrike() {
        var block = new Block(
                Block.Kind.PARAGRAPH,
                "",
                List.of(new Span("code", Style.PLAIN.coded()), new Span("gone", Style.PLAIN.struck())),
                List.of(),
                "",
                ""
        );
        var spans = render(block, 40).getFirst().spans();
        assertFalse(spans.getFirst().style().underline());
        assertFalse(spans.getFirst().style().background().isEmpty());
        assertTrue(spans.getLast().style().strike());
    }

    @Test
    void retainsFoldingPositionsAndExpandsDirectOutput() {
        var details = new Block(
                Block.Kind.DETAILS,
                "",
                List.of(),
                List.of(Block.text(Block.Kind.PARAGRAPH, "SECRET")),
                "",
                "",
                new Block.Format("More", "", "", 1, 1, 0, false)
        );
        var doc = new Document(List.of(details, Block.text(Block.Kind.PARAGRAPH, "after")), Path.of("."));
        var closed = layout.render(doc, 30, Appearance.DARK, Set.of(), true);
        var open = layout.render(doc, 30, Appearance.LIGHT, Set.of(0), true);
        assertFalse(closed.stream().anyMatch(l -> l.text().contains("SECRET")));
        assertTrue(open.stream().anyMatch(l -> l.text().contains("SECRET")));
        assertTrue(layout
            .render(doc, 12, Appearance.DARK, Set.of(0), true)
            .stream()
            .anyMatch(l -> l.text().contains("SECRET"))
        );
        assertEquals(
                closed
                    .stream()
                    .filter(l -> l.text().equals("after"))
                    .findFirst()
                    .orElseThrow()
                    .blockIndex(),
                open
                    .stream()
                    .filter(l -> l.text().equals("after"))
                    .findFirst()
                    .orElseThrow()
                    .blockIndex()
        );
        assertTrue(layout
            .render(doc, 30, Appearance.DARK)
            .stream()
            .anyMatch(l -> l.text().contains("SECRET")));
    }

    @Test
    void hasAsciiFallbackAndOptionalBands() {
        Layout ascii =
                new Layout((text, language, appearance) -> List.of(new Span(text)), new RenderOptions("ascii", false));
        var block = container(Block.Kind.TABLE, container(Block.Kind.ROW, cell("x", 1, 1, "left")));
        var lines = ascii.render(new Document(List.of(block), Path.of(".")), 20, Appearance.LIGHT);
        assertEquals("+---+", lines.getFirst().text());
        assertTrue(lines
            .stream()
            .flatMap(l -> l.spans().stream())
            .allMatch(s -> s.style().background().isEmpty()));
    }

    @Test
    void splitsLiteralLinesWithoutChangingUnicodeWhitespaceOrStyles() {
        var bold = Style.PLAIN.bolded();
        var lines = TextLayout.wrap(
                List.of(
                        new Span("\té 👩‍💻\r\n", bold),
                        new Span("x".repeat(100_000)),
                        new Span("\r", bold),
                        new Span("\nend\n")
                ),
                3,
                true
        );
        assertEquals(List.of(new Span("    é 👩‍💻", bold)), lines.getFirst());
        assertEquals(List.of(new Span("x".repeat(100_000)), new Span("\r", bold)), lines.get(1));
        assertEquals(List.of(new Span("end")), lines.get(2));
        assertTrue(lines.getLast().isEmpty());
        assertEquals(List.of(List.of()), TextLayout.wrap(List.of(), 1, true));
    }

    @Test
    void preservesGraphemesAndVerseWhitespace() {
        var text = "é é 👩‍💻";
        String joined = TextLayout
            .wrap(List.of(new Span(text)), 3, false)
            .stream()
            .flatMap(List::stream)
            .map(Span::text)
            .collect(Collectors.joining());
        assertTrue(joined.contains("é"));
        assertTrue(joined.contains("👩‍💻"));
        assertTrue(render(Block.text(Block.Kind.VERSE, "   verse\n next"), 30).getFirst().text().contains("   verse"));
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import java.nio.file.Path;
import java.util.List;

class LinkRenderingTest {
    private final Layout layout = new Layout((text, language, appearance) -> List.of(new Span(text)));

    @TableTest(
            """
        target                                                             | icon
        https://example.org/document                                       | eb14
        https://github.com/owner/repo/issues/42#issuecomment-1             | eb0c
        https://github.com/owner/repo/pull/42/files                        | ea64
        https://github.com/owner/repo/pulls                                | ea64
        https://gitlab.com/group/project/-/issues/12?view=all              | eb0c
        https://gitlab.com/group/project/-/merge_requests/12               | ea64
        https://gitlab.example.org/group/subgroup/project/-/issues/12      | eb0c
        https://gitlab.example.org/group/project/-/merge_requests/12/diffs | ea64
        https://github.com.example.org/owner/repo/issues/42                | eb14
        https://example.org/post/issues/42                                 | eb14
        """
    )
    void decoratesForgeLinksAndRetainsTheTargetOnTheIcon(String target, @ConvertWith(HexIcon.class) char icon) {
        var block = new Block(
                Block.Kind.PARAGRAPH,
                "",
                List.of(new Span("reference", Style.PLAIN.linked(target))),
                List.of(),
                "",
                ""
        );
        var lines = layout.render(new Document(List.of(block), Path.of(".")), 40, Appearance.DARK);
        String glyph = Character.toString(icon);
        assertEquals("reference " + glyph, lines.getFirst().text());
        assertTrue(lines
            .getFirst()
            .spans()
            .stream()
            .allMatch(span -> span.style().link().equals(target)));
        assertEquals(target, lines.getFirst().linkAt(10));
    }

    public static class HexIcon extends SimpleArgumentConverter {
        @Override
        protected Object convert(Object source, Class<?> targetType) {
            return (char) Integer.parseInt(source.toString(), 16);
        }
    }

    @Test
    void mixedInlineStylesReceiveOneSuffixThatWrapsWithTheLastWord() {
        String target = "https://example.org";
        var block = new Block(
                Block.Kind.PARAGRAPH,
                "",
                List.of(
                        new Span("Read "),
                        new Span("the ", Style.PLAIN.linked(target)),
                        new Span("API", Style.PLAIN.linked(target).coded())
                ),
                List.of(),
                "",
                ""
        );
        var lines = layout.render(new Document(List.of(block), Path.of(".")), 7, Appearance.DARK);
        assertEquals(
                1,
                lines
                    .stream()
                    .mapToLong(line -> line
                        .text()
                        .codePoints()
                        .filter(cp -> cp == 0xeb14)
                        .count())
                    .sum()
        );
        assertTrue(lines
            .stream()
            .anyMatch(line -> line.text().contains("API ")), lines.toString());
    }

    @Test
    void linkHitTestingUsesTerminalColumnsAndRejectsUnsafeTargets() {
        var row = new RenderedLine(
                List.of(
                        new Span("中 "),
                        new Span("é", Style.PLAIN.linked("https://example.org")),
                        new Span(" end"),
                        new Span("unsafe", Style.PLAIN.linked("javascript:alert(1)"))
                ),
                0,
                null
        );
        assertEquals("", row.linkAt(-1));
        assertEquals("", row.linkAt(1));
        assertEquals("https://example.org", row.linkAt(3));
        assertEquals("https://example.org", row.linkAt(4));
        assertEquals("", row.linkAt(5));
        assertEquals("", row.linkAt(10));
        assertEquals("", row.linkAt(100));
        var scrolled = new RenderedLine(
                List.of(new Span("a中"), new Span("link", Style.PLAIN.linked("https://example.org"))),
                0,
                null
        );
        assertEquals("https://example.org", scrolled.linkAt(0, 2));
        var linkedWide = new RenderedLine(
                List.of(new Span("中", Style.PLAIN.linked("https://example.org")), new Span("plain")),
                0,
                null
        );
        assertEquals("", linkedWide.linkAt(0, 1));
        var emoji = new RenderedLine(
                List.of(new Span("👩‍💻"), new Span("link", Style.PLAIN.linked("https://example.org"))),
                0,
                null
        );
        assertEquals("https://example.org", emoji.linkAt(0, 1));
        assertEquals(5, RenderedLine.textOffset("👩‍💻text", 1));
    }

    @Test
    void frontMatterHasLiteralRowsAndOneUnpaintedHorizontalRule() {
        var block = new Block(
                Block.Kind.CODE,
                "",
                List.of(new Span("---\ntitle: Example\ntags:\n  - java\n---")),
                List.of(),
                "yaml",
                "",
                new Block.Format("", "front-matter", "left", 1, 1, 0, false)
        );
        for (String icons : List.of("nerd", "ascii")) {
            var renderer =
                    new Layout((text, language, appearance) -> List.of(new Span(text)), new RenderOptions(icons, false));
            var lines = renderer.render(new Document(List.of(block), Path.of(".")), 30, Appearance.DARK);
            assertEquals(
                    List.of("title: Example", "tags:", "  - java", (icons.equals("ascii") ? "-" : "─").repeat(30), ""),
                    lines.stream().map(RenderedLine::text).toList()
            );
            assertTrue(lines
                .stream()
                .flatMap(line -> line.spans().stream())
                .allMatch(span -> span.style().background().isEmpty())
            );
        }
        var ordinary = new Block(Block.Kind.CODE, "", List.of(new Span("title: Example")), List.of(), "yaml", "");
        assertTrue(layout
            .render(new Document(List.of(ordinary), Path.of(".")), 30, Appearance.DARK)
            .getFirst()
            .text()
            .startsWith("┌─")
        );
    }
}

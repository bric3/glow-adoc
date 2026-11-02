// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.tamboui.text.CharWidth;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import org.tabletest.junit.TableTest;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class LayoutQualityTest {
    @TableTest("""
        Width | Appearance
        16    | DARK
        40    | LIGHT
        120   | DARK
        """)
    void tableWrappingRetainsLinkedUnicodeAndDuplicateCellContent(int width, Appearance appearance) {
        String target = "https://github.com/owner/project/issues/42";
        var cell = new Block(
                Block.Kind.CELL,
                "",
                List.of(new Span("API 👩‍💻 é", Style.PLAIN.linked(target))),
                List.of(),
                "",
                ""
        );
        var row = new Block(Block.Kind.ROW, "", List.of(), List.of(cell, cell), "", "");
        var table = new Block(Block.Kind.TABLE, "", List.of(), List.of(row, row), "", "");
        var renderer = new Layout((source, language, mode) -> List.of(new Span(source)));
        var lines = renderer.render(new Document(List.of(table), Path.of(".")), width, appearance);
        var linked = lines
            .stream()
            .flatMap(line -> line.spans().stream())
            .filter(span -> span.style().link().equals(target))
            .toList();
        String actual = linked.stream().map(Span::text).collect(Collectors.joining());
        for (String token : List.of("API", "👩‍💻", "é", "")) {
            assertEquals(4, actual.split(Pattern.quote(token), -1).length - 1, actual);
        }
        assertTrue(linked
            .stream()
            .allMatch(span -> span.style().foreground().equals(Palette.forAppearance(appearance).accent()))
        );
        assertTrue(lines
            .stream()
            .allMatch(line -> CharWidth.of(line.text()) <= width));
    }
}

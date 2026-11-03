// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import java.util.List;

class AnsiTest {
    @Test
    void dimmingBlendsBackgroundsAndNormalisesReverseVideo() {
        var style = dev.tamboui.style.Style.EMPTY.fg(Color.WHITE).bg(Color.rgb(200, 100, 50)).bold();
        var canvas = Color.Rgb.fromHex("#000000");
        var dimmed = Ansi.dim(style, canvas, true, true);
        assertEquals(Color.rgb(80, 40, 20), dimmed.bg().orElseThrow());
        assertFalse(dimmed.effectiveModifiers().contains(Modifier.BOLD));
        assertFalse(dimmed.effectiveModifiers().contains(Modifier.DIM));
        var reversed = Ansi.dim(style.reversed(), canvas, true, true);
        assertEquals(Color.rgb(80, 40, 20), reversed.fg().orElseThrow());
        assertFalse(reversed.effectiveModifiers().contains(Modifier.REVERSED));
        assertFalse(Ansi.dim(style, canvas, true, false).bg().orElseThrow() instanceof Color.Rgb);
        assertEquals(style.bg(), Ansi.dim(style, canvas, false, false).bg());
        assertFalse(
                Ansi.dim(style.reversed(), canvas, false, false).effectiveModifiers().contains(Modifier.REVERSED),
                "Monochrome status bars must lose their bright reverse-video fill when dimmed"
        );
    }

    @TableTest(
            """
        canvas    | accent    | truecolour
        '#0d1117' | '#79c0ff' | true
        '#0d1117' | '#79c0ff' | false
        '#ffffff' | '#0969da' | true
        '#ffffff' | '#0969da' | false
        """
    )
    void roundedCalloutEndsMatchTheDimmedCentre(String canvas, String accent, boolean truecolour) {
        var background = Color.Rgb.fromHex(canvas);
        var pill = List
            .of(
                    new Span("", Style.PLAIN.colours(accent, "")),
                    new Span(" 1 ", Style.PLAIN.bolded().colours(canvas, accent)),
                    new Span("", Style.PLAIN.colours(accent, ""))
            )
            .stream()
            .map(span -> Ansi.style(span.style(), true, truecolour))
            .toList();
        var dimmed = pill
            .stream()
            .map(style -> Ansi.dim(style, background, true, truecolour))
            .toList();
        assertEquals(dimmed.get(1).bg(), dimmed.getFirst().fg());
        assertEquals(dimmed.get(1).bg(), dimmed.getLast().fg());
        assertFalse(pill.get(1).bg().equals(dimmed.get(1).bg()));
        assertTrue(
                dimmed
                    .stream()
                    .noneMatch(style -> style.effectiveModifiers().contains(Modifier.DIM)),
                "Terminal faint must not change the explicitly blended glyph colour"
        );
    }

    @Test
    void emitsStrikeAndDoesNotUnderlineInlineCode() {
        var spans = List.of(
                new Span("code", Style.PLAIN.coded().colours("#aabbcc", "#223344")),
                new Span("removed", Style.PLAIN.struck())
        );
        String output = Ansi.line(spans, true, true);
        assertTrue(output.contains("\u001B[9m"));
        assertFalse(output.contains("\u001B[4m"));
        assertTrue(Ansi
            .style(Style.PLAIN.struck(), true, true)
            .effectiveModifiers()
            .contains(dev.tamboui.style.Modifier.CROSSED_OUT)
        );
        assertEquals("coderemoved", Ansi.line(spans, false, true));
        assertTrue(Ansi.line(spans, true, false).contains("\u001B[48;5;"), Ansi.line(spans, true, false));
        assertTrue(output.contains("\u001B[48;2;34;51;68m"), output);
    }
}

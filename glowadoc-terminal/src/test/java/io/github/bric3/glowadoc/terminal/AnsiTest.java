// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import org.junit.jupiter.api.Test;
import java.util.List;

class AnsiTest {
    @Test
    void emitsStrikeAndDoesNotUnderlineInlineCode() {
        var spans = List.of(
                new Span("code", Style.PLAIN.coded().colours("#aabbcc", "#223344")),
                new Span("removed", Style.PLAIN.struck())
        );
        String output = Ansi.line(spans, true, true);
        assertTrue(output.contains("[9m"));
        assertFalse(output.contains("[4m"));
        assertTrue(Ansi
            .style(Style.PLAIN.struck(), true, true)
            .effectiveModifiers()
            .contains(dev.tamboui.style.Modifier.CROSSED_OUT)
        );
        assertEquals("coderemoved", Ansi.line(spans, false, true));
        assertTrue(Ansi.line(spans, true, false).contains("[48;5;"), Ansi.line(spans, true, false));
        assertTrue(output.contains("[48;2;34;51;68m"), output);
    }
}

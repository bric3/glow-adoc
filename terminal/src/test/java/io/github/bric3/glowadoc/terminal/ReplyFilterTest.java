// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class ReplyFilterTest {
    @Test
    void separatesFragmentedRepliesAndKeyboardSequences() {
        var replies = new ArrayList<String>();
        var keys = new StringBuilder();
        var filter =
                new ReplyFilter(
                        replies::add,
                        input -> {
                            for (int cp : input) {
                                keys.appendCodePoint(cp);
                            }
                        });
        filter.accept("j\u001b]11;rgb:ffff/".codePoints().toArray());
        filter.accept("ffff/ffff\u001b\\\u001b[Aq".codePoints().toArray());
        assertEquals("j\u001b[Aq", keys.toString());
        assertEquals(1, replies.size());
        assertEquals(255, TerminalSession.channel("ffff"));
        assertEquals(255, TerminalSession.channel("ff"));
        assertEquals(17, TerminalSession.channel("1"));
    }

    @Test
    void preservesColourAndStripsUnsafeLinksInPlainOutput() {
        var span =
                new io.github.bric3.glowadoc.document.Span(
                        "hello",
                        io.github.bric3.glowadoc.document.Style.PLAIN.linked(
                                "https://example.org\u001b]52;x"));
        assertFalse(Ansi.line(java.util.List.of(span), true, true).contains("]8;"));
        assertEquals("hello", Ansi.line(java.util.List.of(span), false, false));
    }
}

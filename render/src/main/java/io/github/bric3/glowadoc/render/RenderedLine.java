// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import dev.tamboui.text.CharWidth;

import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Span;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record RenderedLine(List<Span> spans, int blockIndex, Block asset, int disclosure) {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    public RenderedLine(List<Span> spans, int blockIndex, Block asset) {
        this(spans, blockIndex, asset, -1);
    }

    public RenderedLine {
        spans = List.copyOf(spans);
    }

    public String text() {
        return spans.stream().map(Span::text).collect(Collectors.joining());
    }

    public String linkAt(int column) {
        if (column < 0) {
            return "";
        }
        int start = 0;
        for (Span span : spans) {
            int end = start + CharWidth.of(span.text());
            if (column < end) {
                return Controls.safeLink(span.style().link()) ? span.style().link() : "";
            }
            start = end;
        }
        return "";
    }

    public String linkAt(int column, int horizontalOffset) {
        int skipped = 0;
        for (Span span : spans) {
            int width = CharWidth.of(span.text());
            if (skipped + width <= horizontalOffset) {
                skipped += width;
                continue;
            }
            int offset = textOffset(span.text(), horizontalOffset - skipped);
            skipped += CharWidth.of(span.text().substring(0, offset));
            return column < 0 ? "" : linkAt(column + skipped);
        }
        return "";
    }

    public static int textOffset(String text, int columns) {
        var graphemes = GRAPHEME.matcher(text);
        int cells = 0;
        int offset = 0;
        while (cells < columns && graphemes.find()) {
            cells += CharWidth.of(graphemes.group());
            offset = graphemes.end();
        }
        return offset;
    }
}

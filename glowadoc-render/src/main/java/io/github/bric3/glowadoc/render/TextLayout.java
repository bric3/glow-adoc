// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.render;

import dev.tamboui.text.CharWidth;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TextLayout {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private record Cell(String text, Style style, String callout) {}

    private TextLayout() {
    }

    static int width(List<Span> spans) {
        return spans
            .stream()
            .mapToInt(s -> CharWidth.of(s.text()))
            .sum();
    }

    static void append(List<Span> spans, String text, Style style) {
        append(spans, text, style, "");
    }

    private static void append(List<Span> spans, String text, Style style, String callout) {
        if (text.isEmpty()) {
            return;
        }
        if (!spans.isEmpty() && spans.getLast().style().equals(style) && spans.getLast().callout().equals(callout)) {
            Span last = spans.removeLast();
            spans.add(new Span(last.text() + text, style, callout));
        } else {
            spans.add(new Span(text, style, callout));
        }
    }

    static List<List<Span>> wrap(List<Span> spans, int width, boolean literal) {
        if (literal) {
            return literalLines(spans);
        }
        width = Math.max(1, width);
        var cells = new ArrayList<Cell>();
        for (Span span : spans) {
            var matcher = GRAPHEME.matcher(span.text().replace("\t", "    "));
            while (matcher.find()) {
                cells.add(new Cell(matcher.group(), span.style(), span.callout()));
            }
        }
        var lines = new ArrayList<List<Span>>();
        var line = new ArrayList<Span>();
        int used = 0;
        for (int at = 0; at < cells.size(); ) {
            Cell cell = cells.get(at);
            if (cell.text().equals("\n") || cell.text().equals("\r\n")) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
                at++;
                continue;
            }
            int end = at + 1;
            boolean space = cell.callout().isEmpty() && cell.text().isBlank();
            while (end < cells.size()
                    && !cells.get(end).text().contains("\n")
                    && (cell.callout().isEmpty()
                    ? cells.get(end).callout().isEmpty() && cells.get(end).text().isBlank() == space
                    : cell.callout().equals(cells.get(end).callout()))) {
                end++;
            }
            int wordWidth = 0;
            for (int i = at; i < end; i++) {
                wordWidth += CharWidth.of(cells.get(i).text());
            }
            if (!space && used > 0 && used + wordWidth > width) {
                trim(line);
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            for (; at < end; at++) {
                Cell next = cells.get(at);
                int size = CharWidth.of(next.text());
                if (used + size > width && used > 0) {
                    trim(line);
                    lines.add(line);
                    line = new ArrayList<>();
                    used = 0;
                }
                if (used == 0 && next.callout().isEmpty() && next.text().isBlank()) {
                    continue;
                }
                append(line, next.text(), next.style(), next.callout());
                used += size;
            }
        }
        trim(line);
        lines.add(line);
        return lines;
    }

    private static List<List<Span>> literalLines(List<Span> spans) {
        var lines = new ArrayList<List<Span>>();
        var line = new ArrayList<Span>();
        // Literal text scrolls horizontally, so only line endings need segmentation.
        for (Span span : spans) {
            String text = span.text().replace("\t", "    ");
            int start = 0;
            int end;
            while ((end = text.indexOf('\n', start)) >= 0) {
                int contentEnd = end > start && text.charAt(end - 1) == '\r' ? end - 1 : end;
                append(line, text.substring(start, contentEnd), span.style());
                lines.add(line);
                line = new ArrayList<>();
                start = end + 1;
            }
            append(line, text.substring(start), span.style());
        }
        lines.add(line);
        return lines;
    }

    private static void trim(List<Span> line) {
        while (!line.isEmpty()) {
            if (!line.getLast().callout().isEmpty()) {
                return;
            }
            Span last = line.removeLast();
            String text = last.text().stripTrailing();
            if (!text.isEmpty()) {
                line.add(new Span(text, last.style()));
                break;
            }
        }
    }
}

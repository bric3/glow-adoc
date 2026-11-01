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

    private record Cell(String text, Style style) {}

    private TextLayout() {}

    static int width(List<Span> spans) {
        return spans.stream().mapToInt(s -> CharWidth.of(s.text())).sum();
    }

    static void append(List<Span> spans, String text, Style style) {
        if (text.isEmpty()) {
            return;
        }
        if (!spans.isEmpty() && spans.getLast().style().equals(style)) {
            Span last = spans.removeLast();
            spans.add(new Span(last.text() + text, style));
        } else {
            spans.add(new Span(text, style));
        }
    }

    static List<List<Span>> wrap(List<Span> spans, int width, boolean literal) {
        width = Math.max(1, width);
        var cells = new ArrayList<Cell>();
        for (Span span : spans) {
            var matcher = GRAPHEME.matcher(span.text().replace("\t", "    "));
            while (matcher.find()) {
                cells.add(new Cell(matcher.group(), span.style()));
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
            boolean space = cell.text().isBlank();
            if (!literal) {
                while (end < cells.size()
                        && !cells.get(end).text().contains("\n")
                        && cells.get(end).text().isBlank() == space) {
                    end++;
                }
            }
            int wordWidth = 0;
            for (int i = at; i < end; i++) {
                wordWidth += CharWidth.of(cells.get(i).text());
            }
            if (!literal && !space && used > 0 && used + wordWidth > width) {
                trim(line);
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            for (; at < end; at++) {
                Cell next = cells.get(at);
                int size = CharWidth.of(next.text());
                if (!literal && used + size > width && used > 0) {
                    trim(line);
                    lines.add(line);
                    line = new ArrayList<>();
                    used = 0;
                }
                if (!literal && used == 0 && next.text().isBlank()) {
                    continue;
                }
                append(line, next.text(), next.style());
                used += size;
            }
        }
        if (!literal) {
            trim(line);
        }
        lines.add(line);
        return lines;
    }

    private static void trim(List<Span> line) {
        while (!line.isEmpty()) {
            Span last = line.removeLast();
            String text = last.text().stripTrailing();
            if (!text.isEmpty()) {
                line.add(new Span(text, last.style()));
                break;
            }
        }
    }
}

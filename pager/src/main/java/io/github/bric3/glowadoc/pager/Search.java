// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.pager;

import dev.tamboui.text.CharWidth;

import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.render.RenderedLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Literal search positions and their projection onto styled rendered lines. */
final class Search {
    record Match(int line, int start, int end, int column, int endColumn) {}

    record Part(Span span, boolean matched, boolean active) {}

    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private String query = "";
    private List<Match> matches = List.of();
    private final Map<Integer, List<Match>> byLine = new HashMap<>();
    private int selected = -1;

    String query() {
        return query;
    }

    int count() {
        return matches.size();
    }

    int ordinal() {
        return selected + 1;
    }

    Match active() {
        return selected < 0 ? null : matches.get(selected);
    }

    void update(String query, List<RenderedLine> lines, int originLine, int originColumn) {
        this.query = query;
        var found = new ArrayList<Match>();
        byLine.clear();
        if (!query.isEmpty()) {
            var pattern =
                    Pattern.compile(
                            Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            for (int line = 0; line < lines.size(); line++) {
                String text = lines.get(line).text();
                var matcher = pattern.matcher(text);
                TreeSet<Integer> graphemes = null;
                int from = 0;
                int previous = 0;
                int column = 0;
                while (from <= text.length() && matcher.find(from)) {
                    if (graphemes == null) {
                        graphemes = new TreeSet<>();
                        graphemes.add(0);
                        var clusters = GRAPHEME.matcher(text);
                        while (clusters.find()) {
                            graphemes.add(clusters.end());
                        }
                    }
                    // Highlight whole graphemes, including combining marks and joined emoji.
                    int start = graphemes.floor(matcher.start());
                    int end = graphemes.ceiling(matcher.end());
                    column += CharWidth.of(text.substring(previous, start));
                    var match =
                            new Match(
                                    line,
                                    start,
                                    end,
                                    column,
                                    column + CharWidth.of(text.substring(start, end)));
                    found.add(match);
                    byLine.computeIfAbsent(line, ignored -> new ArrayList<>()).add(match);
                    previous = start;
                    from = text.offsetByCodePoints(matcher.start(), 1);
                }
            }
        }
        matches = List.copyOf(found);
        selected = -1;
        var nearest =
                Comparator.comparingInt((Match match) -> Math.abs(match.line() - originLine))
                        .thenComparingInt(match -> match.line() > originLine ? 1 : 0)
                        .thenComparingInt(match -> Math.abs(match.column() - originColumn))
                        .thenComparingInt(match -> match.column() > originColumn ? 1 : 0);
        for (int i = 0; i < matches.size(); i++) {
            if (selected < 0 || nearest.compare(matches.get(i), matches.get(selected)) < 0) {
                selected = i;
            }
        }
    }

    void next(int direction) {
        if (!matches.isEmpty()) {
            selected = Math.floorMod(selected + direction, matches.size());
        }
    }

    List<Part> parts(int line, List<Span> spans) {
        List<Match> found = byLine.getOrDefault(line, List.of());
        if (found.isEmpty()) {
            return spans.stream().map(span -> new Part(span, false, false)).toList();
        }
        var boundaries = new TreeSet<Integer>();
        boundaries.add(0);
        int offset = 0;
        for (Span span : spans) {
            offset += span.text().length();
            boundaries.add(offset);
        }
        for (Match match : found) {
            boundaries.add(match.start());
            boundaries.add(match.end());
        }
        var result = new ArrayList<Part>();
        Match active = active();
        int previous = 0;
        int spanIndex = 0;
        int spanStart = 0;
        int matchIndex = 0;
        for (int boundary : boundaries) {
            if (boundary == previous) {
                continue;
            }
            while (spanStart + spans.get(spanIndex).text().length() <= previous) {
                spanStart += spans.get(spanIndex++).text().length();
            }
            while (matchIndex < found.size() && found.get(matchIndex).end() <= previous) {
                matchIndex++;
            }
            boolean matched = matchIndex < found.size() && found.get(matchIndex).start() < boundary;
            boolean selected =
                    active != null
                            && active.line() == line
                            && previous < active.end()
                            && boundary > active.start();
            Span span = spans.get(spanIndex);
            result.add(
                    new Part(
                            new Span(
                                    span.text()
                                            .substring(previous - spanStart, boundary - spanStart),
                                    span.style()),
                            matched,
                            selected));
            previous = boundary;
        }
        return result;
    }
}

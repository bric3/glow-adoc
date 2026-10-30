// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import dev.tamboui.text.CharWidth;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.highlight.Highlighter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Layout {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final Pattern WORD = Pattern.compile("\\S+|[^\\S\\n]+|\\n");
    private final Highlighter highlighter;

    public Layout(Highlighter highlighter) {
        this.highlighter = highlighter;
    }

    public List<RenderedLine> render(Document document, int width, Appearance appearance) {
        var lines = new ArrayList<RenderedLine>();
        int[] index = {0};
        for (var block : document.blocks()) {
            render(block, Math.max(1, width), appearance, "", index, lines);
        }
        return lines;
    }

    private void render(
            Block block,
            int width,
            Appearance appearance,
            String indent,
            int[] index,
            List<RenderedLine> lines) {
        int position = index[0]++;
        if (block.kind() == Block.Kind.LIST) {
            for (var item : block.children()) {
                render(item, width, appearance, indent, index, lines);
            }
            lines.add(new RenderedLine(List.of(), position, null));
            return;
        }
        if (block.kind() == Block.Kind.TABLE) {
            for (var row : block.children()) {
                String text =
                        row.children().stream()
                                .map(Block::text)
                                .collect(java.util.stream.Collectors.joining(" | "));
                lines.add(new RenderedLine(List.of(new Span(text)), position, null));
            }
            lines.add(new RenderedLine(List.of(), position, null));
            return;
        }
        boolean code = block.kind() == Block.Kind.CODE || block.kind() == Block.Kind.DIAGRAM;
        List<Span> spans =
                code
                        ? highlighter.highlight(block.text(), block.language(), appearance)
                        : block.spans();
        if (block.kind() == Block.Kind.IMAGE) {
            spans = List.of(new Span("[image: " + block.text() + "] " + block.target()));
        }
        if (block.kind() == Block.Kind.HEADING) {
            spans = spans.stream().map(s -> new Span(s.text(), s.style().bolded())).toList();
        }
        if (block.kind() == Block.Kind.QUOTE) {
            indent += "> ";
        }
        var current = new ArrayList<Span>();
        current.add(new Span(indent));
        int columns = CharWidth.of(indent);
        int first = lines.size();
        for (var span : spans) {
            var words = WORD.matcher(span.text().replace("\t", "    "));
            while (words.find()) {
                String word = words.group();
                int wordWidth = CharWidth.of(word);
                if (!code
                        && !word.isBlank()
                        && wordWidth <= width - CharWidth.of(indent)
                        && columns > CharWidth.of(indent)
                        && columns + wordWidth > width) {
                    lines.add(new RenderedLine(current, position, null));
                    current = new ArrayList<>();
                    current.add(new Span(indent));
                    columns = CharWidth.of(indent);
                }
                var matcher = GRAPHEME.matcher(word);
                var fragment = new StringBuilder();
                while (matcher.find()) {
                    String cluster = matcher.group();
                    if (cluster.equals("\n")
                            || (!code
                                    && columns + CharWidth.of(cluster) > width
                                    && columns > CharWidth.of(indent))) {
                        if (!fragment.isEmpty()) {
                            current.add(new Span(fragment.toString(), span.style()));
                        }
                        fragment.setLength(0);
                        lines.add(new RenderedLine(current, position, null));
                        current = new ArrayList<>();
                        current.add(new Span(indent));
                        columns = CharWidth.of(indent);
                        if (cluster.equals("\n")) {
                            continue;
                        }
                    }
                    fragment.append(cluster);
                    columns += CharWidth.of(cluster);
                }
                if (!fragment.isEmpty()) {
                    current.add(new Span(fragment.toString(), span.style()));
                }
            }
        }
        lines.add(new RenderedLine(current, position, null));
        if (block.kind() == Block.Kind.IMAGE || block.kind() == Block.Kind.DIAGRAM) {
            var line = lines.get(first);
            lines.set(first, new RenderedLine(line.spans(), position, block));
        }
        for (var child : block.children()) {
            render(
                    child,
                    width,
                    appearance,
                    indent + (block.kind() == Block.Kind.ITEM ? "  " : ""),
                    index,
                    lines);
        }
        if (block.kind() != Block.Kind.ITEM) {
            lines.add(new RenderedLine(List.of(), position, null));
        }
    }
}

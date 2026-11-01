// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.highlight.Highlighter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Layout {
    private final Highlighter highlighter;
    private final RenderOptions options;

    public Layout(Highlighter highlighter) {
        this(highlighter, RenderOptions.DEFAULT);
    }

    public Layout(Highlighter highlighter, RenderOptions options) {
        this.highlighter = highlighter;
        this.options = options;
    }

    public List<RenderedLine> render(Document document, int width, Appearance appearance) {
        return render(document, width, appearance, Set.of(), false);
    }

    public List<RenderedLine> render(
            Document document,
            int width,
            Appearance appearance,
            Set<Integer> toggled,
            boolean interactive) {
        var positions = new IdentityHashMap<Block, Integer>();
        for (Block block : document.blocks()) {
            index(block, positions);
        }
        var lines = new ArrayList<RenderedLine>();
        for (Block block : document.blocks()) {
            lines.addAll(
                    render(block, Math.max(1, width), appearance, positions, toggled, interactive));
        }
        // The pager paints its canvas with queried terminal colours.
        if (options.pageBackground() && !interactive) {
            var palette = Palette.forAppearance(appearance);
            lines.replaceAll(
                    line -> {
                        var spans = new ArrayList<Span>();
                        for (Span span : line.spans()) {
                            spans.add(
                                    new Span(
                                            span.text(),
                                            span.style()
                                                    .colours(
                                                            span.style().foreground(),
                                                            span.style().background().isEmpty()
                                                                    ? palette.canvas()
                                                                    : span.style().background())));
                        }
                        spans.add(
                                new Span(
                                        " ".repeat(Math.max(0, width - TextLayout.width(spans))),
                                        Style.PLAIN.colours("", palette.canvas())));
                        return new RenderedLine(
                                spans, line.blockIndex(), line.asset(), line.disclosure());
                    });
        }
        return lines;
    }

    private void index(Block block, Map<Block, Integer> positions) {
        positions.put(block, positions.size());
        for (Block child : block.children()) {
            index(child, positions);
        }
    }

    private List<Span> theme(List<Span> spans, Palette palette) {
        return spans.stream()
                .map(
                        s ->
                                !s.callout().isEmpty()
                                        ? new Span(
                                                callout(s.callout()),
                                                s.style().bolded().colours(palette.accent(), ""))
                                        : s.style().code()
                                                ? new Span(
                                                        s.text(),
                                                        s.style()
                                                                .colours(
                                                                        palette.accent(),
                                                                        palette.stripe()))
                                                : s)
                .toList();
    }

    private List<RenderedLine> render(
            Block block,
            int width,
            Appearance appearance,
            Map<Block, Integer> positions,
            Set<Integer> toggled,
            boolean interactive) {
        int position = positions.get(block);
        Palette palette = Palette.forAppearance(appearance);
        var lines = new ArrayList<RenderedLine>();
        if (block.kind() == Block.Kind.TABLE) {
            for (var row :
                    TextLayout.wrap(theme(block.format().titleSpans(), palette), width, false)) {
                if (!row.isEmpty()) {
                    lines.add(new RenderedLine(row, position, null));
                }
            }
            for (var row :
                    TableLayout.render(block, width, palette, options, s -> theme(s, palette))) {
                lines.add(new RenderedLine(row, position, null));
            }
            lines.add(new RenderedLine(List.of(), position, null));
            return lines;
        }
        boolean source = block.kind() == Block.Kind.CODE || block.kind() == Block.Kind.DIAGRAM;
        boolean quote = block.kind() == Block.Kind.QUOTE || block.kind() == Block.Kind.VERSE;
        boolean box =
                switch (block.kind()) {
                    case CODE, EXAMPLE, SIDEBAR, ADMONITION, DETAILS -> true;
                    default -> false;
                };
        if (width < 5) {
            box = false;
        }
        if (width < 3) {
            quote = false;
        }
        int inset = box ? 4 : quote || block.kind() == Block.Kind.ITEM ? 2 : 0;
        int inner = Math.max(1, width - inset);
        if (block.kind() == Block.Kind.DETAILS) {
            boolean open = !interactive || block.format().open() != toggled.contains(position);
            String marker =
                    options.ascii()
                            ? (open ? "[-] " : "[+] ")
                            : options.icons().equals("nerd")
                                    ? (open ? "\uf0d7 " : "\uf0da ")
                                    : (open ? "▼ " : "▶ ");
            var title = new ArrayList<Span>();
            title.add(new Span(marker));
            title.addAll(
                    block.format().titleSpans().isEmpty()
                            ? List.of(new Span("Details"))
                            : block.format().titleSpans());
            for (var row :
                    TextLayout.wrap(
                            theme(title, palette).stream()
                                    .map(span -> new Span(span.text(), span.style().bolded()))
                                    .toList(),
                            width,
                            false)) {
                lines.add(new RenderedLine(row, position, null, position));
            }
            if (!open) {
                lines.add(new RenderedLine(List.of(), position, null));
                return lines;
            }
        }
        List<Span> spans =
                source
                        ? highlighter.highlight(
                                theme(block.spans(), palette).stream()
                                        .map(Span::text)
                                        .collect(Collectors.joining()),
                                block.language(),
                                appearance)
                        : theme(block.spans(), palette);
        if (block.kind() == Block.Kind.HEADING) {
            spans =
                    spans.stream()
                            .map(
                                    s ->
                                            new Span(
                                                    s.text(),
                                                    s.style()
                                                            .bolded()
                                                            .colours(palette.accent(), "")))
                            .toList();
        }
        if (block.kind() == Block.Kind.IMAGE) {
            spans = List.of(new Span("[image: " + block.text() + "] " + block.target()));
        }
        if (!spans.isEmpty()) {
            for (var row :
                    TextLayout.wrap(spans, inner, source || block.kind() == Block.Kind.VERSE)) {
                lines.add(new RenderedLine(row, position, null));
            }
        }
        for (Block child : block.children()) {
            lines.addAll(render(child, inner, appearance, positions, toggled, interactive));
        }
        if (block.kind() == Block.Kind.IMAGE || block.kind() == Block.Kind.DIAGRAM) {
            if (lines.isEmpty()) {
                lines.add(new RenderedLine(List.of(), position, null));
            }
            RenderedLine first = lines.getFirst();
            lines.set(0, new RenderedLine(first.spans(), position, block));
        }
        if (box) {
            while (!source && !lines.isEmpty() && lines.getLast().text().isBlank()) {
                lines.removeLast();
            }
            boolean admonition = block.kind() == Block.Kind.ADMONITION;
            String title =
                    admonition
                            ? icon(block.format().variant())
                                    + " "
                                    + block.format().variant().toUpperCase(Locale.ROOT)
                            : block.kind() == Block.Kind.DETAILS ? "" : block.format().title();
            String accent =
                    admonition ? palette.admonition(block.format().variant()) : palette.border();
            Style border = Style.PLAIN.colours(accent, palette.surface());
            var framed = new ArrayList<RenderedLine>();
            framed.addAll(lines.stream().filter(line -> line.disclosure() == position).toList());
            boolean inlineCaption =
                    (block.kind() == Block.Kind.CODE || admonition)
                            && !title.isEmpty()
                            && inner >= 4;
            if (!title.isEmpty()) {
                List<Span> caption =
                        admonition
                                ? List.of(
                                        new Span(
                                                title,
                                                palette.admonitionTitleStyle(
                                                        block.format().variant())))
                                : theme(block.format().titleSpans(), palette);
                caption =
                        caption.stream()
                                .map(
                                        span ->
                                                new Span(
                                                        span.text(),
                                                        block.kind() == Block.Kind.CODE
                                                                ? span.style().italicised()
                                                                : span.style().bolded()))
                                .toList();
                if (inlineCaption) {
                    var captionRows = TextLayout.wrap(caption, inner - 2, false);
                    for (int i = 0; i < captionRows.size(); i++) {
                        var row = new ArrayList<Span>();
                        row.add(
                                new Span(
                                        (i == 0
                                                        ? (options.ascii() ? "+--" : "┌──")
                                                        : (options.ascii() ? "|  " : "│  "))
                                                + (admonition ? "" : " "),
                                        border));
                        if (admonition) {
                            row.add(new Span(" ", caption.getFirst().style()));
                        }
                        for (Span span : captionRows.get(i)) {
                            row.add(
                                    new Span(
                                            span.text(),
                                            span.style()
                                                    .colours(
                                                            span.style().foreground().isEmpty()
                                                                    ? border.foreground()
                                                                    : span.style().foreground(),
                                                            span.style().background().isEmpty()
                                                                    ? border.background()
                                                                    : span.style().background())));
                        }
                        if (admonition) {
                            row.add(new Span(" ", caption.getFirst().style()));
                        }
                        int padding = inner - 2 - TextLayout.width(captionRows.get(i));
                        row.add(
                                new Span(
                                        (admonition ? "" : " ")
                                                + (i == 0 ? (options.ascii() ? "-" : "─") : " ")
                                                        .repeat(Math.max(0, padding))
                                                + (i == 0
                                                        ? (options.ascii() ? "+" : "┐")
                                                        : (options.ascii() ? "|" : "│")),
                                        border));
                        framed.add(new RenderedLine(row, position, null));
                    }
                } else {
                    for (var row : TextLayout.wrap(caption, width, false)) {
                        framed.add(new RenderedLine(row, position, null));
                    }
                }
            }
            if (!inlineCaption) {
                framed.add(
                        new RenderedLine(
                                List.of(
                                        new Span(
                                                (options.ascii() ? "+" : "┌")
                                                        + (options.ascii() ? "-" : "─")
                                                                .repeat(inner + 2)
                                                        + (options.ascii() ? "+" : "┐"),
                                                border)),
                                position,
                                null));
            }
            for (RenderedLine line : lines) {
                if (line.disclosure() == position) {
                    continue;
                }
                var row = new ArrayList<Span>();
                row.add(new Span(options.ascii() ? "| " : "│ ", border));
                for (Span span : line.spans()) {
                    Style style = span.style();
                    row.add(
                            new Span(
                                    span.text(),
                                    style.colours(
                                            style.foreground().isEmpty()
                                                    ? palette.foreground()
                                                    : style.foreground(),
                                            style.background().isEmpty()
                                                    ? palette.surface()
                                                    : style.background())));
                }
                row.add(
                        new Span(
                                " ".repeat(Math.max(0, inner - TextLayout.width(line.spans())))
                                        + " ",
                                palette.surfaceStyle()));
                row.add(new Span(options.ascii() ? "|" : "│", border));
                framed.add(
                        new RenderedLine(row, line.blockIndex(), line.asset(), line.disclosure()));
            }
            framed.add(
                    new RenderedLine(
                            List.of(
                                    new Span(
                                            (options.ascii() ? "+" : "└")
                                                    + (options.ascii() ? "-" : "─")
                                                            .repeat(inner + 2)
                                                    + (options.ascii() ? "+" : "┘"),
                                            border)),
                            position,
                            null));
            lines = framed;
        } else if (quote || block.kind() == Block.Kind.ITEM) {
            var indented = new ArrayList<RenderedLine>();
            for (RenderedLine line : lines) {
                var row = new ArrayList<Span>();
                row.add(
                        new Span(
                                quote ? (options.ascii() ? "| " : "│ ") : "  ",
                                palette.borderStyle()));
                row.addAll(line.spans());
                indented.add(
                        new RenderedLine(row, line.blockIndex(), line.asset(), line.disclosure()));
            }
            lines = indented;
        }
        if (block.kind() != Block.Kind.ITEM) {
            lines.add(new RenderedLine(List.of(), position, null));
        }
        return lines;
    }

    private String icon(String kind) {
        if (options.ascii()) {
            return "!";
        }
        if (options.icons().equals("nerd")) {
            return switch (kind) {
                case "tip" -> "\uf0eb";
                case "warning", "caution" -> "\uf071";
                case "important" -> "\uf06a";
                default -> "\uf05a";
            };
        }
        return switch (kind) {
            case "tip" -> "✓";
            case "warning", "caution", "important" -> "!";
            default -> "ⓘ";
        };
    }

    private String callout(String number) {
        if (!options.ascii() && number.matches("[1-9]|10")) {
            return Character.toString(0x2775 + Integer.parseInt(number));
        }
        return "(" + number + ")";
    }
}

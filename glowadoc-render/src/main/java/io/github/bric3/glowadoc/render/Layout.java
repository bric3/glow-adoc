// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.render;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.highlight.Highlighter;
import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Layout {
    private static final Pattern ISSUE_PATH = Pattern.compile("(?:^|/)issues(?:/(?:[0-9]+|new)(?:/.*)?)?/?$");
    private static final Pattern PULL_PATH =
            Pattern.compile("(?:^|/)(?:pulls?|merge_requests)(?:/(?:[0-9]+|new)(?:/.*)?)?/?$");
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
            boolean interactive
    ) {
        var positions = new IdentityHashMap<Block, Integer>();
        for (Block block : document.blocks()) {
            index(block, positions);
        }
        var lines = new ArrayList<RenderedLine>();
        for (Block block : document.blocks()) {
            lines.addAll(render(block, Math.max(1, width), appearance, positions, toggled, interactive, 0));
        }
        // The pager paints its canvas with queried terminal colours.
        if (options.pageBackground() && !interactive) {
            var palette = Palette.forAppearance(appearance);
            lines.replaceAll(line -> {
                var spans = new ArrayList<Span>();
                for (Span span : line.spans()) {
                    spans.add(
                            new Span(
                                    span.text(),
                                    span
                                        .style()
                                        .colours(
                                                span.style().foreground(),
                                                span.style().background().isEmpty() ? palette.canvas() : span
                                                    .style()
                                                    .background()
                                        )
                            )
                    );
                }
                spans.add(
                        new Span(
                                " ".repeat(Math.max(0, width - TextLayout.width(spans))),
                                Style.PLAIN.colours("", palette.canvas())
                        )
                );
                return new RenderedLine(spans, line.blockIndex(), line.asset(), line.disclosure());
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
        var themed = new ArrayList<Span>();
        for (int i = 0; i < spans.size(); i++) {
            Span span = spans.get(i);
            if (!span.callout().isEmpty()) {
                if (options.callouts().equals("pill") && !options.ascii()) {
                    // Keep each pill atomic when prose wraps, including its rounded ends.
                    if (options.icons().equals("nerd")) {
                        themed.add(new Span("", Style.PLAIN.colours(palette.accent(), ""), span.callout()));
                    }
                    themed.add(
                            new Span(
                                    " " + span.callout() + " ",
                                    Style.PLAIN.bolded().colours(palette.canvas(), palette.accent()),
                                    span.callout()
                            )
                    );
                    if (options.icons().equals("nerd")) {
                        themed.add(new Span("", Style.PLAIN.colours(palette.accent(), ""), span.callout()));
                    }
                } else {
                    themed.add(
                            new Span(
                                    callout(span.callout()),
                                    span.style().bolded().colours(palette.accent(), ""),
                                    span.callout()
                            )
                    );
                }
            } else if (span.style().code()) {
                themed.add(new Span(span.text(), span.style().colours(palette.accent(), palette.stripe())));
            } else {
                themed.add(
                        Controls.safeLink(span.style().link())
                        ? new Span(span.text(), span.style().colours(palette.accent(), span.style().background()))
                        : span
                );
            }
            String target = span.style().link();
            if (options.icons().equals("nerd")
                    && Controls.safeLink(target)
                    && (i + 1 == spans.size() || !target.equals(spans.get(i + 1).style().link()))) {
                // A non-breaking space keeps the suffix with the link's final word.
                themed.add(new Span(" " + linkIcon(target), Style.PLAIN.linked(target).colours(palette.accent(), "")));
            }
        }
        return themed;
    }

    private static String linkIcon(String target) {
        try {
            URI uri = URI.create(target);
            String host = uri.getHost();
            String path = uri.getPath();
            boolean forge = host != null
                    && path != null
                    && (host.equalsIgnoreCase("github.com")
                    || host.equalsIgnoreCase("www.github.com")
                    || host.equalsIgnoreCase("gitlab.com")
                    || host.equalsIgnoreCase("www.gitlab.com")
                    || path.contains("/-/"));
            if (forge && ISSUE_PATH.matcher(path).find()) {
                return "";
            }
            if (forge && PULL_PATH.matcher(path).find()) {
                return "";
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed targets still retain their readable labels.
        }
        return "";
    }

    private List<Span> source(Block block, Palette palette, Appearance appearance) {
        var spans = block.spans();
        if (block.format().variant().equals("front-matter")) {
            String text = block.text();
            int first = text.indexOf('\n');
            int last = text.lastIndexOf('\n');
            spans = List.of(new Span(first >= 0 && last > first ? text.substring(first + 1, last) : ""));
        }
        var themed = theme(spans, palette);
        var highlighted = highlighter.highlight(
                themed.stream().map(Span::text).collect(Collectors.joining()),
                block.language(),
                appearance
        );
        if (themed
            .stream()
            .noneMatch(span -> !span.callout().isEmpty())) {
            return highlighted;
        }
        // Syntax spans can split a callout; restore its colours after highlighting the whole
        // source.
        var restored = new ArrayList<Span>();
        int index = 0;
        int position = 0;
        for (Span syntax : highlighted) {
            int offset = 0;
            while (offset < syntax.text().length()) {
                while (position == themed.get(index).text().length()) {
                    index++;
                    position = 0;
                }
                Span original = themed.get(index);
                int count = Math.min(syntax.text().length() - offset, original.text().length() - position);
                restored.add(
                        new Span(
                                syntax.text().substring(offset, offset + count),
                                original.callout().isEmpty() ? syntax.style() : original.style(),
                                original.callout()
                        )
                );
                offset += count;
                position += count;
            }
        }
        return restored;
    }

    private List<RenderedLine> render(
            Block block,
            int width,
            Appearance appearance,
            Map<Block, Integer> positions,
            Set<Integer> toggled,
            boolean interactive,
            int listCalloutWidth
    ) {
        int position = positions.get(block);
        Palette palette = Palette.forAppearance(appearance);
        var lines = new ArrayList<RenderedLine>();
        if (block.imageOnlyParagraph()) {
            for (Block child : block.children()) {
                for (var line : render(child, width, appearance, positions, toggled, interactive, 0)) {
                    lines.add(new RenderedLine(line.spans(), position, null));
                }
            }
            var first = lines.getFirst();
            lines.set(0, new RenderedLine(first.spans(), position, block));
            return lines;
        }
        if (block.kind() == Block.Kind.TABLE) {
            for (var row : TextLayout.wrap(theme(block.format().titleSpans(), palette), width, false)) {
                if (!row.isEmpty()) {
                    lines.add(new RenderedLine(row, position, null));
                }
            }
            for (var row : TableLayout.render(block, width, palette, options, s -> theme(s, palette))) {
                lines.add(new RenderedLine(row, position, null));
            }
            lines.add(new RenderedLine(List.of(), position, null));
            return lines;
        }
        boolean source = block.kind() == Block.Kind.CODE || block.kind() == Block.Kind.DIAGRAM;
        boolean frontMatter = block.kind() == Block.Kind.CODE && block.format().variant().equals("front-matter");
        boolean quote = block.kind() == Block.Kind.QUOTE || block.kind() == Block.Kind.VERSE;
        boolean box = switch (block.kind()) {
            case CODE -> !frontMatter;
            case EXAMPLE, SIDEBAR, ADMONITION, DETAILS -> true;
            default -> false;
        };
        if (width < 5) {
            box = false;
        }
        if (width < 3) {
            quote = false;
        }
        List<Span> calloutMarker = block.kind() == Block.Kind.ITEM
                && !block.spans().isEmpty()
                && !block.spans().getFirst().callout().isEmpty()
                ? theme(List.of(block.spans().getFirst()), palette)
                : List.of();
        int calloutInset = calloutMarker.isEmpty() ? 0 : Math.max(listCalloutWidth, TextLayout.width(calloutMarker)
                + 1);
        if (calloutInset >= width - 2) {
            // Keep existing wrapping when no text column fits beside the marker.
            calloutInset = 0;
        }
        int inset = (box ? 4 : quote || block.kind() == Block.Kind.ITEM ? 2 : 0) + calloutInset;
        int inner = Math.max(1, width - inset);
        if (block.kind() == Block.Kind.DETAILS) {
            boolean open = !interactive || block.format().open() != toggled.contains(position);
            String marker = options.ascii()
                    ? (open ? "[-] " : "[+] ")
                    : options.icons().equals("nerd") ? (open ? " " : " ") : (open ? "▼ " : "▶ ");
            var title = new ArrayList<Span>();
            title.add(new Span(marker));
            title.addAll(
                    block.format().titleSpans().isEmpty() ? List.of(new Span("Details")) : block.format().titleSpans()
            );
            for (var row :
                    TextLayout.wrap(
                            theme(title, palette)
                                .stream()
                                .map(span -> new Span(span.text(), span.style().bolded()))
                                .toList(),
                            width,
                            false
            )) {
                lines.add(new RenderedLine(row, position, null, position));
            }
            if (!open) {
                lines.add(new RenderedLine(List.of(), position, null));
                return lines;
            }
        }
        List<Span> spans = source
                ? source(block, palette, appearance)
                : theme(block.spans().subList(calloutInset > 0 ? 1 : 0, block.spans().size()), palette);
        if (block.kind() == Block.Kind.HEADING) {
            spans = palette.headingSpans(spans);
        }
        if (block.kind() == Block.Kind.IMAGE) {
            Style link =
                    spans
                .stream()
                .map(Span::style)
                .filter(style -> !style.link().isEmpty())
                .findFirst()
                .orElse(Style.PLAIN);
            spans = List.of(new Span("[image: " + block.text() + "] " + block.target(), link));
        }
        if (!spans.isEmpty() || calloutInset > 0) {
            for (var row : TextLayout.wrap(spans, inner, source || block.kind() == Block.Kind.VERSE)) {
                lines.add(new RenderedLine(row, position, null));
            }
        }
        int childCalloutWidth = block.kind() == Block.Kind.LIST
                ? block
            .children()
            .stream()
            .flatMap(child -> child.spans().stream().limit(1))
            .filter(span -> !span.callout().isEmpty())
            .mapToInt(span -> TextLayout.width(theme(List.of(span), palette)) + 1)
            .max()
            .orElse(0)
                : 0;
        for (Block child : block.children()) {
            lines.addAll(render(child, inner, appearance, positions, toggled, interactive, childCalloutWidth));
        }
        if (frontMatter) {
            lines.add(
                    new RenderedLine(
                            List.of(new Span((options.ascii() ? "-" : "─").repeat(width), palette.borderStyle())),
                            position,
                            null
                    )
            );
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
            String title = admonition
                    ? icon(block.format().variant()) + " " + block.format().variant().toUpperCase(Locale.ROOT)
                    : block.kind() == Block.Kind.DETAILS ? "" : block.format().title();
            String accent = admonition ? palette.admonition(block.format().variant()) : palette.border();
            Style border = Style.PLAIN.colours(accent, palette.surface());
            var framed = new ArrayList<RenderedLine>();
            framed.addAll(lines
                .stream()
                .filter(line -> line.disclosure() == position)
                .toList());
            boolean inlineCaption = (block.kind() == Block.Kind.CODE || admonition)
                    && !title.isEmpty()
                    && inner >= 4;
            if (!title.isEmpty()) {
                List<Span> caption = admonition
                        ? List.of(new Span(title, palette.admonitionTitleStyle(block.format().variant())))
                        : theme(block.format().titleSpans(), palette);
                caption = caption
                    .stream()
                    .map(span -> new Span(
                            span.text(),
                            block.kind() == Block.Kind.CODE ? span.style().italicised() : span.style().bolded()
                    ))
                    .toList();
                if (inlineCaption) {
                    var captionRows = TextLayout.wrap(caption, inner - 2, false);
                    for (int i = 0; i < captionRows.size(); i++) {
                        var row = new ArrayList<Span>();
                        row.add(
                                new Span(
                                        (i == 0 ? (options.ascii() ? "+--" : "┌──") : (options.ascii() ? "|  " : "│  ")) + (admonition
                                        ? ""
                                        : " "),
                                        border
                                )
                        );
                        if (admonition) {
                            row.add(new Span(" ", caption.getFirst().style()));
                        }
                        for (Span span : captionRows.get(i)) {
                            row.add(
                                    new Span(
                                            span.text(),
                                            span
                                                .style()
                                                .colours(
                                                        span.style().foreground().isEmpty() ? border.foreground() : span
                                                            .style()
                                                            .foreground(),
                                                        span.style().background().isEmpty() ? border.background() : span
                                                            .style()
                                                            .background()
                                                )
                                    )
                            );
                        }
                        if (admonition) {
                            row.add(new Span(" ", caption.getFirst().style()));
                        }
                        int padding = inner - 2 - TextLayout.width(captionRows.get(i));
                        row.add(
                                new Span(
                                        (admonition ? "" : " ")
                                        + (i == 0 ? (options.ascii() ? "-" : "─") : " ").repeat(Math.max(0, padding))
                                        + (i == 0 ? (options.ascii() ? "+" : "┐") : (options.ascii() ? "|" : "│")),
                                        border
                                )
                        );
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
                                                (options.ascii() ? "+" : "┌") + (options.ascii() ? "-" : "─")
                                                    .repeat(inner + 2) + (options.ascii() ? "+" : "┐"),
                                                border
                                        )
                                ),
                                position,
                                null
                        )
                );
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
                                            style.foreground().isEmpty() ? palette.foreground() : style.foreground(),
                                            style.background().isEmpty() ? palette.surface() : style.background()
                                    )
                            )
                    );
                }
                row.add(
                        new Span(
                                " ".repeat(Math.max(0, inner - TextLayout.width(line.spans()))) + " ",
                                palette.surfaceStyle()
                        )
                );
                row.add(new Span(options.ascii() ? "|" : "│", border));
                framed.add(new RenderedLine(row, line.blockIndex(), line.asset(), line.disclosure()));
            }
            framed.add(
                    new RenderedLine(
                            List.of(
                                    new Span(
                                            (options.ascii() ? "+" : "└") + (options.ascii() ? "-" : "─")
                                                .repeat(inner + 2) + (options.ascii() ? "+" : "┘"),
                                            border
                                    )
                            ),
                            position,
                            null
                    )
            );
            lines = framed;
        } else if (quote || block.kind() == Block.Kind.ITEM) {
            var indented = new ArrayList<RenderedLine>();
            for (RenderedLine line : lines) {
                var row = new ArrayList<Span>();
                row.add(new Span(quote ? (options.ascii() ? "| " : "│ ") : "  ", palette.borderStyle()));
                if (calloutInset > 0) {
                    if (indented.isEmpty()) {
                        row.addAll(calloutMarker);
                    }
                    row.add(
                            new Span(" "
                                .repeat(calloutInset - (indented.isEmpty() ? TextLayout.width(calloutMarker) : 0))
                            )
                    );
                }
                row.addAll(line.spans());
                indented.add(new RenderedLine(row, line.blockIndex(), line.asset(), line.disclosure()));
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
                case "tip" -> "";
                case "warning" -> "";
                case "caution" -> Character.toString(0xf0238);
                case "important" -> "";
                default -> "";
            };
        }
        return switch (kind) {
            case "tip" -> "✓";
            case "warning", "caution", "important" -> "!";
            default -> "ⓘ";
        };
    }

    private String callout(String number) {
        if (!options.ascii() && !options.callouts().equals("ascii") && number.matches("[1-9]|10")) {
            return Character.toString(0x2775 + Integer.parseInt(number));
        }
        return "(" + number + ")";
    }
}

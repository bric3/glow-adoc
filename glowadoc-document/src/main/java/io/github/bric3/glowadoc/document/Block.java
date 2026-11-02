// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.document;

import java.util.List;
import java.util.stream.Collectors;

public record Block(
        Kind kind,
        String id,
        List<Span> spans,
        List<Block> children,
        String language,
        String target,
        Format format
) {
    public enum Kind {
        HEADING,
        PARAGRAPH,
        CODE,
        LIST,
        ITEM,
        QUOTE,
        VERSE,
        ADMONITION,
        EXAMPLE,
        SIDEBAR,
        OPEN,
        DETAILS,
        TABLE,
        ROW,
        CELL,
        IMAGE,
        DIAGRAM
    }

    public record Format(
            List<Span> titleSpans,
            String variant,
            String alignment,
            int columnSpan,
            int rowSpan,
            int columnWidth,
            boolean open,
            String frame,
            String grid
    ) {
        public Format {
            titleSpans = List.copyOf(titleSpans);
        }

        public Format(
                String title,
                String variant,
                String alignment,
                int columnSpan,
                int rowSpan,
                int columnWidth,
                boolean open
        ) {
            this(
                    title.isEmpty() ? List.of() : List.of(new Span(title)),
                    variant,
                    alignment,
                    columnSpan,
                    rowSpan,
                    columnWidth,
                    open,
                    "all",
                    "all"
            );
        }

        public String title() {
            return titleSpans.stream().map(Span::text).collect(Collectors.joining());
        }

        public static final Format DEFAULT = new Format("", "", "left", 1, 1, 0, false);
    }

    public Block(Kind kind, String id, List<Span> spans, List<Block> children, String language, String target) {
        this(kind, id, spans, children, language, target, Format.DEFAULT);
    }

    public Block {
        spans = List.copyOf(spans);
        children = List.copyOf(children);
    }

    public String text() {
        return spans.stream().map(Span::text).collect(Collectors.joining());
    }

    public boolean imageOnlyParagraph() {
        return kind == Kind.PARAGRAPH
                && spans.isEmpty()
                && !children.isEmpty()
                && children.stream().allMatch(child -> child.kind() == Kind.IMAGE);
    }

    public static Block text(Kind kind, String text) {
        return new Block(kind, "", List.of(new Span(text)), List.of(), "", "");
    }
}

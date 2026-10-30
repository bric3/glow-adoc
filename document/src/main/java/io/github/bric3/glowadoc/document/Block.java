// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.document;

import java.util.List;

public record Block(
        Kind kind,
        String id,
        List<Span> spans,
        List<Block> children,
        String language,
        String target) {
    public enum Kind {
        HEADING,
        PARAGRAPH,
        CODE,
        LIST,
        ITEM,
        QUOTE,
        ADMONITION,
        TABLE,
        ROW,
        CELL,
        IMAGE,
        DIAGRAM
    }

    public Block {
        spans = List.copyOf(spans);
        children = List.copyOf(children);
    }

    public String text() {
        return spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining());
    }

    public static Block text(Kind kind, String text) {
        return new Block(kind, "", List.of(new Span(text)), List.of(), "", "");
    }
}

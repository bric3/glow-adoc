// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Span;

import java.util.List;
import java.util.stream.Collectors;

public record RenderedLine(List<Span> spans, int blockIndex, Block asset, int disclosure) {
    public RenderedLine(List<Span> spans, int blockIndex, Block asset) {
        this(spans, blockIndex, asset, -1);
    }

    public RenderedLine {
        spans = List.copyOf(spans);
    }

    public String text() {
        return spans.stream().map(Span::text).collect(Collectors.joining());
    }
}

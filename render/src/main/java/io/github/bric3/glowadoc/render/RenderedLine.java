// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Span;

import java.util.List;

public record RenderedLine(List<Span> spans, int blockIndex, Block asset) {
    public RenderedLine {
        spans = List.copyOf(spans);
    }

    public String text() {
        return spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining());
    }
}

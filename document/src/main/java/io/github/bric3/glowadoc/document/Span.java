// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.document;

public record Span(String text, Style style) {
    public Span(String text) {
        this(text, Style.PLAIN);
    }
}

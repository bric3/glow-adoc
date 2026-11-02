// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.document;

public record Span(String text, Style style, String callout) {
    public Span(String text, Style style) {
        this(text, style, "");
    }

    public Span(String text) {
        this(text, Style.PLAIN);
    }

    public static Span callout(String number) {
        return new Span("<" + number + ">", Style.PLAIN, number);
    }
}

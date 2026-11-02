// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.document;

public record Style(
        boolean bold,
        boolean italic,
        boolean code,
        boolean underline,
        String foreground,
        String background,
        String link,
        boolean strike
) {
    public static final Style PLAIN = new Style(false, false, false, false, "", "", "", false);

    public Style(
            boolean bold,
            boolean italic,
            boolean code,
            boolean underline,
            String foreground,
            String background,
            String link
    ) {
        this(bold, italic, code, underline, foreground, background, link, false);
    }

    public Style bolded() {
        return new Style(true, italic, code, underline, foreground, background, link, strike);
    }

    public Style italicised() {
        return new Style(bold, true, code, underline, foreground, background, link, strike);
    }

    public Style coded() {
        return new Style(bold, italic, true, underline, foreground, background, link, strike);
    }

    public Style struck() {
        return new Style(bold, italic, code, underline, foreground, background, link, true);
    }

    public Style linked(String target) {
        return new Style(bold, italic, code, true, foreground, background, target, strike);
    }

    public Style colours(String fg, String bg) {
        return new Style(bold, italic, code, underline, fg, bg, link, strike);
    }
}

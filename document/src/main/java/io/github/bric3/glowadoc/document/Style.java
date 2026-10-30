// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.document;

public record Style(
        boolean bold,
        boolean italic,
        boolean code,
        boolean underline,
        String foreground,
        String background,
        String link) {
    public static final Style PLAIN = new Style(false, false, false, false, "", "", "");

    public Style bolded() {
        return new Style(true, italic, code, underline, foreground, background, link);
    }

    public Style italicised() {
        return new Style(bold, true, code, underline, foreground, background, link);
    }

    public Style coded() {
        return new Style(bold, italic, true, underline, foreground, background, link);
    }

    public Style linked(String target) {
        return new Style(bold, italic, code, true, foreground, background, target);
    }
}

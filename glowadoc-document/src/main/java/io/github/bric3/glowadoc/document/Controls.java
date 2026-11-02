// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.document;

public final class Controls {
    private Controls() {
    }

    public static String visible(String text) {
        var result = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if ((cp >= 32 && (cp < 127 || cp > 159)) || cp == '\n' || cp == '\t') {
                result.appendCodePoint(cp);
            } else if (cp == 27) {
                result.append("^[");
            }
        });
        return result.toString();
    }

    public static boolean safeLink(String link) {
        return !link.isBlank()
                && link.codePoints().noneMatch(cp -> cp < 32 || cp == 127 || (cp >= 128 && cp <= 159))
                && (link.startsWith("https://") || link.startsWith("http://") || link.startsWith("mailto:"));
    }
}

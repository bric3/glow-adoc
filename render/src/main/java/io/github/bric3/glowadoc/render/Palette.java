// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Style;

public record Palette(
        String foreground, String surface, String stripe, String border, String accent) {
    public static Palette forAppearance(Appearance appearance) {
        return appearance == Appearance.LIGHT
                ? new Palette("#24292f", "#f0f2f5", "#e4e8ee", "#57606a", "#0969da")
                : new Palette("#e6edf3", "#21262d", "#2d333b", "#8b949e", "#79c0ff");
    }

    public Style borderStyle() {
        return Style.PLAIN.colours(border, "");
    }

    public String canvas() {
        return foreground.equals("#24292f") ? "#ffffff" : "#0d1117";
    }

    public Style surfaceStyle() {
        return Style.PLAIN.colours(foreground, surface);
    }

    public Style statusStyle() {
        return Style.PLAIN.colours(foreground, stripe);
    }

    public String admonition(String kind) {
        return switch (kind) {
            case "tip" -> "#2f9e44";
            case "warning" -> "#e5534b";
            case "important", "caution" -> "#c69026";
            default -> accent;
        };
    }
}

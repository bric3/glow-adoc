// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.render;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Style;

public record Palette(String foreground, String surface, String stripe, String border, String accent) {
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
        boolean light = foreground.equals("#24292f");
        return switch (kind) {
            case "tip" -> light ? "#1a7f37" : "#3fb950";
            case "important" -> light ? "#8250df" : "#bc8cff";
            case "warning" -> light ? "#cf222e" : "#ff7b72";
            case "caution" -> light ? "#9a6700" : "#d29922";
            default -> accent;
        };
    }

    Style admonitionTitleStyle(String kind) {
        String background = admonition(kind);
        int rgb = Integer.parseInt(background.substring(1), 16);
        // WCAG relative luminance selects the higher-contrast black or white text.
        double luminance = 0.2126 * linear(rgb >> 16)
                + 0.7152 * linear((rgb >> 8) & 255)
                + 0.0722 * linear(rgb & 255);
        double blackContrast = (luminance + 0.05) / 0.05;
        double whiteContrast = 1.05 / (luminance + 0.05);
        return Style.PLAIN.bolded().colours(blackContrast >= whiteContrast ? "#000000" : "#ffffff", background);
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import java.util.List;

public final class Ansi {
    private Ansi() {
    }

    public static dev.tamboui.style.Style style(Style span, boolean colour, boolean truecolour) {
        var result = dev.tamboui.style.Style.EMPTY;
        if (Controls.safeLink(span.link())) {
            result = result.hyperlink(span.link());
        }
        if (!colour) {
            return result;
        }
        if (span.bold()) {
            result = result.bold();
        }
        if (span.italic()) {
            result = result.italic();
        }
        if (span.underline()) {
            result = result.underlined();
        }
        if (span.strike()) {
            result = result.crossedOut();
        }
        if (!span.foreground().isBlank()) {
            result = result.fg(color(span.foreground(), truecolour));
        }
        if (!span.background().isBlank()) {
            result = result.bg(color(span.background(), truecolour));
        }
        return result;
    }

    private static Color color(String hex, boolean truecolour) {
        return color(Color.Rgb.fromHex(hex), truecolour);
    }

    private static Color color(Color.Rgb rgb, boolean truecolour) {
        if (truecolour) {
            return rgb;
        }
        return Color.indexed(
                16 + 36 * Math.round(rgb.r() / 255f * 5) + 6 * Math.round(rgb.g() / 255f * 5) + Math.round(
                        rgb.b() / 255f * 5
                )
        );
    }

    public static dev.tamboui.style.Style dim(
            dev.tamboui.style.Style style,
            Color.Rgb background,
            boolean colour,
            boolean truecolour
    ) {
        var result = style.notBold();
        if (!colour) {
            return result.notReversed().dim();
        }
        result = result.notDim();
        var fill = style.bg().orElse(background);
        if (style.effectiveModifiers().contains(Modifier.REVERSED)) {
            fill = style.fg().orElse(background);
            result = result.notReversed().fg(style.bg().orElse(background));
        }
        // Rounded callout ends use foreground paint; their centres use background paint.
        if (result.fg().isPresent()) {
            result = result.fg(blend(result.fg().orElseThrow(), background, truecolour));
        }
        return result.bg(blend(fill, background, truecolour));
    }

    private static Color blend(Color colour, Color.Rgb background, boolean truecolour) {
        var rgb = colour.toRgb();
        return color(
                new Color.Rgb(
                        (rgb.r() * 2 + background.r() * 3) / 5,
                        (rgb.g() * 2 + background.g() * 3) / 5,
                        (rgb.b() * 2 + background.b() * 3) / 5
                ),
                truecolour
        );
    }

    public static String line(List<Span> spans, boolean colour, boolean truecolour) {
        var out = new StringBuilder();
        for (var span : spans) {
            if (colour) {
                var style = style(span.style(), true, truecolour);
                out.append("\u001B[0m");
                if (span.style().bold()) {
                    out.append("\u001B[1m");
                }
                if (span.style().italic()) {
                    out.append("\u001B[3m");
                }
                if (span.style().underline()) {
                    out.append("\u001B[4m");
                }
                if (span.style().strike()) {
                    out.append("\u001B[9m");
                }
                style
                    .fg()
                    .ifPresent(c -> out.append("\u001B[").append(c.toAnsiForeground()).append('m'));
                style
                    .bg()
                    .ifPresent(c -> out.append("\u001B[").append(c.toAnsiBackground()).append('m'));
                if (Controls.safeLink(span.style().link())) {
                    out.append("\u001B]8;;").append(span.style().link()).append("\u001B\\");
                }
            }
            out.append(Controls.visible(span.text()));
            if (colour && Controls.safeLink(span.style().link())) {
                out.append("\u001B]8;;\u001B\\");
            }
        }
        if (colour) {
            out.append("\u001B[0m");
        }
        return out.toString();
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import dev.tamboui.style.Color;
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
        var rgb = Color.Rgb.fromHex(hex);
        if (truecolour) {
            return rgb;
        }
        return Color.indexed(
                16 + 36 * Math.round(rgb.r() / 255f * 5) + 6 * Math.round(rgb.g() / 255f * 5) + Math.round(
                        rgb.b() / 255f * 5
                )
        );
    }

    public static String line(List<Span> spans, boolean colour, boolean truecolour) {
        var out = new StringBuilder();
        for (var span : spans) {
            if (colour) {
                var style = style(span.style(), true, truecolour);
                out.append("[0m");
                if (span.style().bold()) {
                    out.append("[1m");
                }
                if (span.style().italic()) {
                    out.append("[3m");
                }
                if (span.style().underline()) {
                    out.append("[4m");
                }
                if (span.style().strike()) {
                    out.append("[9m");
                }
                style
                    .fg()
                    .ifPresent(c -> out.append("[").append(c.toAnsiForeground()).append('m'));
                style
                    .bg()
                    .ifPresent(c -> out.append("[").append(c.toAnsiBackground()).append('m'));
                if (Controls.safeLink(span.style().link())) {
                    out.append("]8;;").append(span.style().link()).append("\\");
                }
            }
            out.append(Controls.visible(span.text()));
            if (colour && Controls.safeLink(span.style().link())) {
                out.append("]8;;\\");
            }
        }
        if (colour) {
            out.append("[0m");
        }
        return out.toString();
    }
}

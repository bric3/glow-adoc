// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

/** Shared viewport geometry for direct output and the pager. */
public record ContentArea(int width, int margin) {
    public static ContentArea within(int terminalWidth, int maximum, boolean centered) {
        int available = Math.max(1, terminalWidth - 1);
        int width = maximum > 0 ? Math.min(maximum, available) : available;
        return new ContentArea(width, centered ? Math.max(0, (available - width) / 2) : 0);
    }
}

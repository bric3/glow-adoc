// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

public record RenderOptions(
        String icons,
        boolean tableStripes,
        boolean pageBackground,
        String tableFrame,
        String tableGrid,
        String tableSectionBorders,
        String callouts) {
    public RenderOptions(
            String icons,
            boolean tableStripes,
            boolean pageBackground,
            String tableFrame,
            String tableGrid,
            String tableSectionBorders) {
        this(
                icons,
                tableStripes,
                pageBackground,
                tableFrame,
                tableGrid,
                tableSectionBorders,
                "pill");
    }

    public RenderOptions(String icons, boolean tableStripes) {
        this(icons, tableStripes, false, "auto", "auto", "thin");
    }

    public static final RenderOptions DEFAULT = new RenderOptions("nerd", true);

    public boolean ascii() {
        return icons.equals("ascii");
    }
}

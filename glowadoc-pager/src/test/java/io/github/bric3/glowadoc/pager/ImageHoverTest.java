// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.pager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.github.bric3.glowadoc.terminal.KittyImages;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;

class ImageHoverTest {
    @Test
    void usesVisibleCroppedBoundsAndContentMargin() {
        var placements = List.of(new KittyImages.Placement(7, Path.of("image.png"), 0, 5, 10, 3, 2, 2.0));
        assertEquals(7, ReaderPager.hoveredImage(placements, 5, 0));
        assertEquals(7, ReaderPager.hoveredImage(placements, 14, 2));
        assertEquals(-1, ReaderPager.hoveredImage(placements, 4, 0));
        assertEquals(-1, ReaderPager.hoveredImage(placements, 15, 0));
        assertEquals(-1, ReaderPager.hoveredImage(placements, 5, 3));
    }
}

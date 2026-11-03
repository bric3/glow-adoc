// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.pager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Color;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.render.Layout;
import io.github.bric3.glowadoc.render.RenderOptions;
import io.github.bric3.glowadoc.render.Palette;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;

class StructureMenuTest {
    @Test
    void selectsCurrentSectionAndNavigatesSkippedLevelsAndDuplicateTitles() {
        var menu = menu(
                heading("Document", 0),
                heading("Same", 1),
                heading("Same", 3),
                Block.text(Block.Kind.PARAGRAPH, "Body"),
                heading("Next", 1)
        );
        menu.open(3);
        assertEquals(2, menu.selected().position());
        menu.key(KeyEvent.ofKey(KeyCode.LEFT));
        assertEquals(1, menu.selected().position());
        assertEquals(3, menu.visible().size());
        menu.key(KeyEvent.ofKey(KeyCode.RIGHT));
        assertEquals(1, menu.selected().position());
        menu.key(KeyEvent.ofKey(KeyCode.DOWN));
        assertEquals(2, menu.key(KeyEvent.ofKey(KeyCode.ENTER)));
        assertFalse(menu.isOpen());
    }

    @TableTest(
            """
        position | collapsed | key   | selected | visible
        0        | false     | LEFT  | 0        | 2
        0        | true      | LEFT  | 0        | 2
        1        | false     | LEFT  | 1        | 3
        1        | true      | LEFT  | 0        | 3
        2        | false     | LEFT  | 1        | 4
        0        | false     | RIGHT | 0        | 4
        0        | true      | RIGHT | 0        | 4
        1        | false     | RIGHT | 1        | 4
        1        | true      | RIGHT | 1        | 4
        2        | false     | RIGHT | 2        | 4
        """
    )
    void arrowKeysCollapseBeforeSelectingParentAndExpandWithoutMovingSelection(
            int position,
            boolean collapsed,
            String key,
            int selected,
            int visible
    ) {
        var menu = menu(heading("Root", 1), heading("Branch", 2), heading("Leaf", 3), heading("Next", 1));
        menu.open(position);
        if (collapsed) {
            menu.key(KeyEvent.ofChar('-'));
        }
        menu.key(KeyEvent.ofKey(KeyCode.valueOf(key)));
        assertEquals(selected, menu.selected().position());
        assertEquals(visible, menu.visible().size());
    }

    @TableTest("""
        drag
        false
        true
        """)
    void mouseMotionSelectsTheHoveredRowWithoutActivatingIt(boolean drag) {
        var menu = menu(heading("Root", 1), heading("Child", 2));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 80, 24));
        menu.draw(buffer, Appearance.DARK, false, false);
        var bounds = menu.bounds();
        menu.mouse(
                drag
                ? MouseEvent.drag(MouseButton.LEFT, bounds.x() + 10, bounds.y() + 2)
                : MouseEvent.move(bounds.x() + 10, bounds.y() + 2)
        );
        assertEquals("Child", menu.selected().title());
        assertTrue(menu.isOpen());
        menu.draw(buffer, Appearance.DARK, false, false);
        assertFalse(buffer
            .get(bounds.x() + 5, bounds.y() + 1)
            .style()
            .effectiveModifiers()
            .contains(Modifier.REVERSED));
        assertTrue(buffer
            .get(bounds.x() + 5, bounds.y() + 2)
            .style()
            .effectiveModifiers()
            .contains(Modifier.REVERSED));
        menu.mouse(MouseEvent.move(0, 0));
        assertEquals("Child", menu.selected().title());
        menu.mouse(MouseEvent.move(bounds.x() + 1, bounds.y() + 1));
        assertEquals("Root", menu.selected().title());
    }

    @Test
    void draggingAcrossRowsUpdatesSelectionAndCancelsClickActivation() {
        var menu = menu(heading("Root", 1), heading("Child", 2));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 80, 24));
        menu.draw(buffer, Appearance.DARK, false, false);
        var bounds = menu.bounds();
        int x = bounds.x() + 10;
        menu.mouse(MouseEvent.press(MouseButton.LEFT, x, bounds.y() + 1));
        menu.mouse(MouseEvent.drag(MouseButton.LEFT, x, bounds.y() + 2));
        assertEquals("Child", menu.selected().title());
        assertEquals(-1, menu.mouse(MouseEvent.release(MouseButton.LEFT, x, bounds.y() + 2)));
        assertTrue(menu.isOpen());
    }

    @Test
    void rendersContinuousBordersTrianglesAndStyledArrowKeyHints() {
        var menu = menu(heading("Root", 1), heading("Child", 2));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 80, 24));
        menu.draw(buffer, Appearance.DARK, true, true);
        var bounds = menu.bounds();
        assertEquals("┌", buffer.get(bounds.x(), bounds.y()).symbol());
        assertEquals("─", buffer.get(bounds.x() + 1, bounds.y()).symbol());
        assertEquals("┐", buffer.get(bounds.x() + bounds.width() - 1, bounds.y()).symbol());
        assertEquals("│", buffer.get(bounds.x(), bounds.y() + 1).symbol());
        assertEquals("└", buffer.get(bounds.x(), bounds.y() + bounds.height() - 1).symbol());
        assertEquals("▼", buffer.get(bounds.x() + 2, bounds.y() + 1).symbol());
        int footer = bounds.y() + bounds.height() - 2;
        var palette = Palette.forAppearance(Appearance.DARK);
        assertEquals(Color.hex(palette.stripe()), buffer.get(bounds.x() + 1, footer).style().bg().orElseThrow());
        assertEquals("↑", buffer.get(bounds.x() + 2, footer).symbol());
        assertTrue(buffer.get(bounds.x() + 2, footer).style().effectiveModifiers().contains(Modifier.BOLD));
        assertFalse(buffer.get(bounds.x() + 5, footer).style().effectiveModifiers().contains(Modifier.BOLD));
        assertTrue(text(buffer).contains("← collapse/parent"));
        assertTrue(text(buffer).contains("→ expand"));
        assertFalse(text(buffer).contains("+/-"));
        menu.key(KeyEvent.ofKey(KeyCode.LEFT));
        menu.draw(buffer, Appearance.DARK, true, true);
        assertTrue(text(buffer).contains("▶ Root"));
    }

    @TableTest("""
        row | column
        0   | 1
        0   | 3
        0   | 74
        1   | 1
        1   | 4
        1   | 74
        """)
    void clickingEmptySpaceInAnEntryJumpsToThatSection(int row, int column) {
        var menu = menu(heading("Root", 1), heading("Child", 2));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 80, 24));
        menu.draw(buffer, Appearance.DARK, false, false);
        var bounds = menu.bounds();
        int x = bounds.x() + column;
        int y = bounds.y() + 1 + row;
        assertEquals(-1, menu.mouse(MouseEvent.press(MouseButton.LEFT, x, y)));
        assertEquals(row, menu.mouse(MouseEvent.release(MouseButton.LEFT, x, y)));
        assertFalse(menu.isOpen());
    }

    @Test
    void retainsExpansionAndRevealsTheCurrentPathOnReopening() {
        var menu = menu(heading("First", 1), heading("Child", 2), heading("Next", 1));
        menu.open(0);
        menu.key(KeyEvent.ofChar('-'));
        assertEquals(List.of("First", "Next"), menu.visible().stream().map(StructureMenu.Entry::title).toList());
        menu.key(KeyEvent.ofKey(KeyCode.ESCAPE));
        assertFalse(menu.isOpen());
        menu.open(0);
        assertEquals(2, menu.visible().size());
        menu.key(KeyEvent.ofChar('+'));
        assertEquals(3, menu.visible().size());
        menu.key(KeyEvent.ofChar('-'));
        menu.open(1);
        assertEquals(3, menu.visible().size());
        assertEquals("Child", menu.selected().title());
    }

    @Test
    void markerClicksToggleAndTitleClicksJumpWhileDragsAndOutsideClicksDoNothing() {
        var menu = menu(heading("First", 1), heading("Child", 2));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 80, 24));
        menu.draw(buffer, Appearance.DARK, false, false);
        var bounds = menu.bounds();
        int x = bounds.x() + 2;
        int y = bounds.y() + 1;
        assertEquals(-1, menu.mouse(MouseEvent.press(MouseButton.LEFT, x, y)));
        assertEquals(-1, menu.mouse(MouseEvent.release(MouseButton.LEFT, x, y)));
        assertEquals(1, menu.visible().size());
        assertTrue(menu.isOpen());
        menu.mouse(MouseEvent.press(MouseButton.LEFT, x + 3, y));
        menu.mouse(MouseEvent.drag(MouseButton.LEFT, x + 4, y));
        assertEquals(-1, menu.mouse(MouseEvent.release(MouseButton.LEFT, x + 3, y)));
        assertEquals(-1, menu.mouse(MouseEvent.press(MouseButton.LEFT, 0, 0)));
        assertEquals(-1, menu.mouse(MouseEvent.release(MouseButton.LEFT, 0, 0)));
        menu.mouse(MouseEvent.press(MouseButton.LEFT, x + 3, y));
        assertEquals(0, menu.mouse(MouseEvent.release(MouseButton.LEFT, x + 3, y)));
        assertFalse(menu.isOpen());
    }

    @Test
    void keepsKeyboardSelectionVisibleAndHandlesSmallTerminalsAndEmptyDocuments() {
        var menu = menu(IntStream
            .range(0, 40)
            .mapToObj(i -> heading("Section " + i + " 界é", 1))
            .toArray(Block[]::new));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 30, 10));
        menu.draw(buffer, Appearance.DARK, false, false);
        menu.key(KeyEvent.ofKey(KeyCode.END));
        menu.draw(buffer, Appearance.DARK, false, false);
        assertEquals(39, menu.selected().position());
        assertTrue(text(buffer).contains("Section 39"));
        menu.key(KeyEvent.ofKey(KeyCode.HOME));
        menu.key(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        assertTrue(menu.selected().position() > 0);
        menu.key(KeyEvent.ofKey(KeyCode.PAGE_UP));
        assertEquals(0, menu.selected().position());
        for (int size = 1; size < 8; size++) {
            var small = Buffer.empty(new Rect(0, 0, size, size));
            menu.draw(small, Appearance.DARK, false, false);
        }
        var empty = menu(heading("Document", 0));
        empty.open(0);
        empty.draw(buffer, Appearance.DARK, false, false);
        assertTrue(text(buffer).contains("No sections"));
        assertEquals(-1, empty.key(KeyEvent.ofKey(KeyCode.ENTER)));
        empty.key(KeyEvent.ofKey(KeyCode.ESCAPE));
        assertFalse(empty.isOpen());
    }

    @Test
    void revealsHeadingInsideClosedDisclosuresUsingLayoutPositions() {
        var closed = new Block(Block.Kind.DETAILS, "", List.of(), List.of(heading("Hidden", 1)), "", "");
        var menu = menu(closed, heading("Visible", 1));
        menu.open(1);
        var toggled = new HashSet<Integer>();
        menu.reveal(toggled);
        assertEquals(Set.of(0), toggled);
        menu.reveal(toggled);
        assertEquals(Set.of(0), toggled);
        var layout = new Layout((text, language, appearance) -> List.of(new Span(text)), RenderOptions.DEFAULT);
        var document = new Document(List.of(closed, heading("Visible", 1)), Path.of("."));
        assertFalse(layout
            .render(document, 20, Appearance.DARK, Set.of(), true)
            .stream()
            .anyMatch(line -> line.blockIndex() == 1)
        );
        assertTrue(layout
            .render(document, 10, Appearance.DARK, toggled, true)
            .stream()
            .anyMatch(line -> line.blockIndex() == menu.selected().position() && line.text().contains("Hidden"))
        );
    }

    @Test
    void wheelScrollsWithoutMovingSelectionOrSnappingBackOnRedraw() {
        var menu = menu(IntStream
            .range(0, 40)
            .mapToObj(i -> heading("Section " + i, 1))
            .toArray(Block[]::new));
        menu.open(0);
        var buffer = Buffer.empty(new Rect(0, 0, 80, 12));
        menu.draw(buffer, Appearance.DARK, false, false);
        var bounds = menu.bounds();
        menu.mouse(MouseEvent.scrollDown(bounds.x() + 3, bounds.y() + 2));
        menu.draw(buffer, Appearance.DARK, false, false);
        assertFalse(text(buffer).contains("Section 0"));
        assertTrue(text(buffer).contains("Section 3"));
        assertEquals(0, menu.selected().position());
        menu.key(KeyEvent.ofKey(KeyCode.DOWN));
        menu.draw(buffer, Appearance.DARK, false, false);
        assertTrue(text(buffer).contains("Section 1"));
    }

    @Test
    void clearsUnderlyingStylesAndLinksWhileLeavingTheBackgroundDimmed() {
        var buffer = Buffer.empty(new Rect(0, 0, 80, 24));
        var background = Style.EMPTY.dim().bold().underlined().hyperlink("https://example.org");
        buffer.setStyle(buffer.area(), background);
        var menu = menu(heading("界é title", 1));
        menu.open(0);
        menu.draw(buffer, Appearance.DARK, false, false);
        var bounds = menu.bounds();
        var cell = buffer.get(bounds.x() + 4, bounds.y() + 1);
        assertEquals("界", cell.symbol());
        assertTrue(cell.style().hyperlink().isEmpty());
        assertFalse(cell.style().effectiveModifiers().contains(Modifier.DIM));
        assertFalse(cell.style().effectiveModifiers().contains(Modifier.UNDERLINED));
        assertTrue(buffer.get(0, 0).style().effectiveModifiers().contains(Modifier.DIM));
        assertTrue(text(buffer).contains("界é title"));
    }

    private static StructureMenu menu(Block... blocks) {
        return new StructureMenu(new Document(List.of(blocks), Path.of(".")));
    }

    private static String text(Buffer buffer) {
        return IntStream
            .range(0, buffer.height())
            .mapToObj(y -> IntStream
                .range(0, buffer.width())
                .mapToObj(x -> buffer.get(x, y).symbol())
                .collect(Collectors.joining()))
            .collect(Collectors.joining("\n"));
    }

    private static Block heading(String text, int level) {
        return new Block(
                Block.Kind.HEADING,
                "",
                List.of(new Span(text)),
                List.of(),
                "",
                "",
                Block.Format.DEFAULT,
                level
        );
    }
}

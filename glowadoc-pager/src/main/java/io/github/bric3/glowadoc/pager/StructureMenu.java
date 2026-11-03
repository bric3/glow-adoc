// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.pager;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.style.Hyperlink;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.render.Palette;
import io.github.bric3.glowadoc.terminal.Ansi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class StructureMenu {
    record Entry(int position, String title, int parent, int depth, List<Integer> disclosures) {}

    private final List<Block> blocks = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final Set<Integer> collapsed = new HashSet<>();
    private List<Entry> visible = List.of();
    private boolean open;
    private int selected;
    private int offset;
    private int rows = 1;
    private Rect bounds = new Rect(0, 0, 0, 0);
    private int pressed = -1;
    private boolean pressedMarker;

    StructureMenu(Document document) {
        var parents = new ArrayList<Integer>();
        for (Block block : document.blocks()) {
            index(block, List.of(), parents);
        }
        updateVisible();
    }

    private void index(Block block, List<Integer> disclosures, List<Integer> parents) {
        // Layout assigns block positions in preorder, including children hidden by disclosures.
        int position = blocks.size();
        blocks.add(block);
        if (block.kind() == Block.Kind.HEADING && block.headingLevel() > 0) {
            while (!parents.isEmpty()
                    && blocks.get(entries.get(parents.getLast()).position()).headingLevel() >= block.headingLevel()) {
                parents.removeLast();
            }
            entries.add(
                    new Entry(
                            position,
                            block.text(),
                            parents.isEmpty() ? -1 : parents.getLast(),
                            parents.size(),
                            disclosures
                    )
            );
            parents.add(entries.size() - 1);
        }
        if (block.kind() == Block.Kind.DETAILS) {
            disclosures = new ArrayList<>(disclosures);
            disclosures.add(position);
            disclosures = List.copyOf(disclosures);
        }
        for (Block child : block.children()) {
            index(child, disclosures, parents);
        }
    }

    boolean isOpen() {
        return open;
    }

    void open(int position) {
        open = true;
        pressed = -1;
        offset = 0;
        selected = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).position() > position) {
                break;
            }
            selected = i;
        }
        if (!entries.isEmpty()) {
            int parent = entries.get(selected).parent();
            while (parent >= 0) {
                collapsed.remove(parent);
                parent = entries.get(parent).parent();
            }
        }
        updateVisible();
        ensureVisible();
    }

    Entry selected() {
        return entries.isEmpty() ? null : entries.get(selected);
    }

    List<Entry> visible() {
        return visible;
    }

    Rect bounds() {
        return bounds;
    }

    int key(KeyEvent key) {
        pressed = -1;
        if (key.isKey(KeyCode.ESCAPE)) {
            open = false;
        } else if (key.isKey(KeyCode.ENTER)) {
            return activate();
        } else if (!entries.isEmpty()) {
            int row = visible.indexOf(selected());
            if (key.isUp() || key.isChar('k')) {
                selectRow(row - 1);
            } else if (key.isDown() || key.isChar('j')) {
                selectRow(row + 1);
            } else if (key.isPageUp()) {
                selectRow(row - rows);
            } else if (key.isPageDown()) {
                selectRow(row + rows);
            } else if (key.isKey(KeyCode.HOME)) {
                selectRow(0);
            } else if (key.isKey(KeyCode.END)) {
                selectRow(visible.size() - 1);
            } else if (key.isChar('-')) {
                collapse();
            } else if (key.isChar('+')) {
                collapsed.remove(selected);
                updateVisible();
            } else if (key.isLeft()) {
                if (hasChildren(selected) && !collapsed.contains(selected)) {
                    collapse();
                } else if (selected().parent() >= 0) {
                    selected = selected().parent();
                }
            } else if (key.isRight() && hasChildren(selected)) {
                if (collapsed.remove(selected)) {
                    updateVisible();
                }
            }
            ensureVisible();
        }
        return -1;
    }

    int mouse(MouseEvent pointer) {
        if (pointer.kind() == MouseEventKind.DRAG) {
            pressed = -1;
        }
        int row = pointer.y() - bounds.y() - 1;
        boolean inside =
                pointer.x() > bounds.x() && pointer.x() < bounds.x() + bounds.width() - 1 && row >= 0 && row < rows;
        if ((pointer.kind() == MouseEventKind.MOVE || pointer.kind() == MouseEventKind.DRAG)
                && inside
                && offset + row < visible.size()) {
            selected = entries.indexOf(visible.get(offset + row));
        }
        if (inside && pointer.isScroll()) {
            pressed = -1;
            if (pointer.kind() == MouseEventKind.SCROLL_UP || pointer.kind() == MouseEventKind.SCROLL_DOWN) {
                offset = Math.clamp(
                        offset + (pointer.kind() == MouseEventKind.SCROLL_UP ? -3 : 3),
                        0,
                        Math.max(0, visible.size() - rows)
                );
            }
        } else if (pointer.isPress()) {
            pressed = -1;
            if (inside && pointer.isLeftButton() && offset + row < visible.size()) {
                Entry entry = visible.get(offset + row);
                int index = entries.indexOf(entry);
                pressedMarker = pointer.x() == markerColumn(entry) && hasChildren(index);
                pressed = index;
            }
        } else if (pointer.isRelease()) {
            if (inside
                    && pointer.isLeftButton()
                    && offset + row < visible.size()
                    && pressed == entries.indexOf(visible.get(offset + row))) {
                selected = pressed;
                pressed = -1;
                boolean marker = pointer.x() == markerColumn(selected()) && hasChildren(selected);
                if (marker && pressedMarker) {
                    if (!collapsed.remove(selected)) {
                        collapse();
                    }
                    updateVisible();
                    ensureVisible();
                } else if (!marker && !pressedMarker) {
                    return activate();
                }
            }
            pressed = -1;
        }
        return -1;
    }

    private int activate() {
        if (entries.isEmpty()) {
            return -1;
        }
        open = false;
        return selected().position();
    }

    void reveal(Set<Integer> toggled) {
        for (int position : selected().disclosures()) {
            if (blocks.get(position).format().open()) {
                toggled.remove(position);
            } else {
                toggled.add(position);
            }
        }
    }

    private void collapse() {
        if (hasChildren(selected)) {
            collapsed.add(selected);
            updateVisible();
        }
    }

    private boolean hasChildren(int index) {
        return index + 1 < entries.size() && entries.get(index + 1).parent() == index;
    }

    private void updateVisible() {
        var next = new ArrayList<Entry>();
        int hiddenDepth = Integer.MAX_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.depth() > hiddenDepth) {
                continue;
            }
            next.add(entry);
            hiddenDepth = collapsed.contains(i) ? entry.depth() : Integer.MAX_VALUE;
        }
        visible = List.copyOf(next);
    }

    private void selectRow(int row) {
        selected = entries.indexOf(visible.get(Math.clamp(row, 0, visible.size() - 1)));
    }

    private void ensureVisible() {
        if (entries.isEmpty()) {
            offset = 0;
            return;
        }
        int row = visible.indexOf(selected());
        offset = Math.clamp(offset, Math.max(0, row - rows + 1), Math.max(0, row));
        offset = Math.min(offset, Math.max(0, visible.size() - rows));
    }

    private int markerColumn(Entry entry) {
        return bounds.x() + 2 + Math.min(entry.depth() * 2, Math.max(0, bounds.width() - 8));
    }

    void draw(Buffer buffer, Appearance appearance, boolean colour, boolean truecolour) {
        var palette = Palette.forAppearance(appearance);
        var base = colour ? Ansi.style(palette.surfaceStyle(), true, truecolour) : Style.EMPTY;
        var border = colour ? base.patch(Ansi.style(palette.borderStyle(), true, truecolour)) : base;
        var status = colour ? Ansi.style(palette.statusStyle(), true, truecolour) : base.reversed();
        var key = colour
                ? Ansi.style(
                        palette.surfaceStyle().bolded().colours(palette.accent(), palette.stripe()),
                        true,
                        truecolour
        )
                : status.bold();
        var selection = colour
                ? Ansi.style(palette.surfaceStyle().colours(palette.canvas(), palette.accent()), true, truecolour)
                : base.reversed();
        int width = Math.min(80, Math.max(1, buffer.width() - 4));
        int height = Math.min(Math.max(4, visible.size() + 3), Math.max(1, buffer.height() - 4));
        bounds = new Rect((buffer.width() - width) / 2, (buffer.height() - height) / 2, width, height);
        int nextRows = Math.max(1, height - 3);
        if (rows != nextRows) {
            rows = nextRows;
            ensureVisible();
        }
        // Remove document styles and hyperlinks before painting the opaque menu.
        buffer.clear(bounds);
        buffer.setStyle(bounds, base);
        for (int y = bounds.y(); y < bounds.y() + height; y++) {
            buffer.setString(bounds.x(), y, " ".repeat(width), base);
        }
        if (width < 4 || height < 4) {
            write(buffer, bounds.x(), bounds.y(), "Structure", base, width);
            return;
        }
        buffer.setString(bounds.x(), bounds.y(), "┌" + "─".repeat(width - 2) + "┐", border);
        buffer.setString(bounds.x(), bounds.y() + height - 1, "└" + "─".repeat(width - 2) + "┘", border);
        write(buffer, bounds.x() + 2, bounds.y(), " Structure ", border, width - 4);
        for (int y = bounds.y() + 1; y < bounds.y() + height - 1; y++) {
            buffer.setString(bounds.x(), y, "│", border);
            buffer.setString(bounds.x() + width - 1, y, "│", border);
        }
        if (visible.isEmpty()) {
            write(buffer, bounds.x() + 2, bounds.y() + 1, "No sections", base, width - 4);
        }
        for (int i = offset; i < Math.min(visible.size(), offset + rows); i++) {
            Entry entry = visible.get(i);
            int index = entries.indexOf(entry);
            int y = bounds.y() + 1 + i - offset;
            Style style = index == selected ? selection : base;
            buffer.setString(bounds.x() + 1, y, " ".repeat(width - 2), style);
            int x = markerColumn(entry);
            String marker = hasChildren(index) ? collapsed.contains(index) ? "▶ " : "▼ " : "  ";
            var spans = new ArrayList<Span>();
            spans.add(Span.styled(marker, style));
            for (var span : palette.headingSpans(blocks.get(entry.position()).spans())) {
                // Keep entry activation on the section when heading text contains a link.
                var titleStyle = base.patch(Ansi.style(span.style(), colour, truecolour)).hyperlink((Hyperlink) null);
                if (index == selected) {
                    titleStyle = titleStyle.patch(selection);
                }
                spans.add(Span.styled(span.text(), titleStyle));
            }
            buffer.setLine(x, y, Line.from(spans), bounds.x() + width - 1 - x);
        }
        int footer = bounds.y() + height - 2;
        buffer.setString(bounds.x() + 1, footer, " ".repeat(width - 2), status);
        buffer.setLine(bounds.x() + 2, footer, controls(status, key), width - 4);
    }

    private static Line controls(Style status, Style key) {
        return Line.from(
                dev.tamboui.text.Span.styled("↑↓", key),
                dev.tamboui.text.Span.styled(" select  ", status),
                dev.tamboui.text.Span.styled("←", key),
                dev.tamboui.text.Span.styled(" collapse/parent  ", status),
                dev.tamboui.text.Span.styled("→", key),
                dev.tamboui.text.Span.styled(" expand  ", status),
                dev.tamboui.text.Span.styled("Enter", key),
                dev.tamboui.text.Span.styled(" jump  ", status),
                dev.tamboui.text.Span.styled("Esc", key),
                dev.tamboui.text.Span.styled(" close", status)
        );
    }

    private static void write(Buffer buffer, int x, int y, String text, Style style, int width) {
        buffer.setLine(
                x,
                y,
                Line.styled(CharWidth.substringByWidth(text, Math.max(0, width)), style),
                Math.max(0, width)
        );
    }
}

// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.render;

import dev.tamboui.text.CharWidth;

import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

final class TableLayout {
    private record Cell(Block block, int row, int column, int rows, int columns) {}

    private TableLayout() {}

    static List<List<Span>> render(
            Block table,
            int width,
            Palette palette,
            RenderOptions options,
            Function<List<Span>, List<Span>> theme) {
        var grid = new ArrayList<List<Cell>>();
        var cells = new ArrayList<Cell>();
        int rowCount = table.children().size();
        for (int r = 0; r < rowCount; r++) {
            ensure(grid, r);
            int column = 0;
            for (Block block : table.children().get(r).children()) {
                int rows = Math.min(block.format().rowSpan(), rowCount - r);
                int columns = block.format().columnSpan();
                for (int offset = 0; offset < columns; offset++) {
                    if (get(grid, r, column + offset) != null) {
                        column += offset + 1;
                        offset = -1;
                    }
                }
                // Bound the physical grid even for adversarial documents.
                if (column + columns > 256) {
                    throw new IllegalArgumentException("Table exceeds 256 columns");
                }
                Cell cell = new Cell(block, r, column, rows, columns);
                cells.add(cell);
                for (int y = r; y < r + rows; y++) {
                    ensure(grid, y);
                    for (int x = column; x < column + columns; x++) {
                        while (grid.get(y).size() <= x) {
                            grid.get(y).add(null);
                        }
                        grid.get(y).set(x, cell);
                    }
                }
                column += columns;
            }
        }
        int count = grid.stream().mapToInt(List::size).max().orElse(0);
        if (count == 0) {
            return List.of();
        }
        int[] widths = new int[count];
        Arrays.fill(widths, 1);
        for (Cell cell : cells) {
            int desired =
                    theme.apply(cell.block().spans()).stream()
                            .mapToInt(s -> CharWidth.of(s.text()))
                            .sum();
            for (int c = cell.column(); c < cell.column() + cell.columns(); c++) {
                widths[c] = Math.max(widths[c], Math.max(1, desired / cell.columns()));
            }
        }
        int budget = Math.max(count, width - 3 * count - 1);
        int[] weights = new int[count];
        for (Cell cell : cells) {
            if (cell.columns() > 1) {
                continue;
            }
            for (int c = cell.column(); c < cell.column() + cell.columns(); c++) {
                weights[c] = Math.max(weights[c], cell.block().format().columnWidth());
            }
        }
        int totalWeight = Arrays.stream(weights).sum();
        if (totalWeight > 0) {
            for (int c = 0; c < count; c++) {
                widths[c] = Math.max(1, budget * weights[c] / totalWeight);
            }
        }
        while (Arrays.stream(widths).sum() > budget) {
            int largest = 0;
            for (int c = 1; c < count; c++) {
                if (widths[c] > widths[largest]) {
                    largest = c;
                }
            }
            if (widths[largest] <= 1) {
                break;
            }
            widths[largest]--;
        }
        var content = new HashMap<Cell, List<List<Span>>>();
        int[] heights = new int[rowCount];
        Arrays.fill(heights, 1);
        for (Cell cell : cells) {
            int cellWidth = 3 * (cell.columns() - 1);
            for (int c = cell.column(); c < cell.column() + cell.columns(); c++) {
                cellWidth += widths[c];
            }
            var spans = new ArrayList<>(theme.apply(cell.block().spans()));
            collectChildren(cell.block(), spans, theme);
            var wrapped = TextLayout.wrap(spans, cellWidth, false);
            content.put(cell, wrapped);
            if (cell.rows() == 1) {
                heights[cell.row()] = Math.max(heights[cell.row()], wrapped.size());
            }
        }
        for (Cell cell : cells) {
            int height = 0;
            for (int r = cell.row(); r < cell.row() + cell.rows(); r++) {
                height += heights[r];
            }
            if (height < content.get(cell).size()) {
                heights[cell.row() + cell.rows() - 1] += content.get(cell).size() - height;
            }
        }
        var result = new ArrayList<List<Span>>();
        var borders = Borders.resolve(table, options);
        addRule(result, grid, widths, 0, rowCount, borders, false, palette, options);
        for (int r = 0; r < rowCount; r++) {
            boolean section = section(table.children().get(r));
            String bg = rowBackground(table, r, options, palette);
            Style base = Style.PLAIN.colours(palette.foreground(), bg);
            Style border = palette.borderStyle();
            for (int y = 0; y < heights[r]; y++) {
                var line = new ArrayList<Span>();
                boolean edge = vertical(grid, r, 0, count, borders);
                line.add(
                        new Span(edge ? (options.ascii() ? "|" : "│") : " ", edge ? border : base));
                for (int c = 0; c < count; ) {
                    Cell cell = get(grid, r, c);
                    int span = cell == null ? 1 : cell.columns();
                    int w = 3 * (span - 1);
                    for (int x = c; x < c + span; x++) {
                        w += widths[x];
                    }
                    List<Span> text = List.of();
                    if (cell != null) {
                        int offset = y;
                        for (int prior = cell.row(); prior < r; prior++) {
                            offset += heights[prior];
                        }
                        if (offset < content.get(cell).size()) {
                            text = content.get(cell).get(offset);
                        }
                    }
                    int remaining = Math.max(0, w - TextLayout.width(text));
                    String align = cell == null ? "" : cell.block().format().alignment();
                    int left =
                            align.equals("right")
                                    ? remaining
                                    : align.equals("center") ? remaining / 2 : 0;
                    line.add(new Span(" ".repeat(left + 1), base));
                    for (Span s : text) {
                        Style style =
                                s.style()
                                        .colours(
                                                s.style().foreground().isEmpty()
                                                        ? base.foreground()
                                                        : s.style().foreground(),
                                                s.style().background().isEmpty()
                                                        ? bg
                                                        : s.style().background());
                        if (section
                                || cell != null
                                        && cell.block().format().variant().equals("header")) {
                            style = style.bolded();
                        }
                        line.add(new Span(s.text(), style));
                    }
                    line.add(new Span(" ".repeat(remaining - left + 1), base));
                    c += span;
                    boolean boundary = vertical(grid, r, c, count, borders);
                    line.add(
                            new Span(
                                    boundary ? (options.ascii() ? "|" : "│") : " ",
                                    boundary ? border : base));
                }
                result.add(line);
            }
            int boundary = r + 1;
            boolean sectionRule =
                    boundary < rowCount
                            && (table.children().get(r).format().variant().equals("header")
                                    || table.children()
                                            .get(boundary)
                                            .format()
                                            .variant()
                                            .equals("footer"));
            addRule(
                    result,
                    grid,
                    widths,
                    boundary,
                    rowCount,
                    borders,
                    sectionRule,
                    palette,
                    options);
        }
        return result;
    }

    private record Borders(
            boolean sides, boolean ends, boolean rows, boolean columns, boolean heavy) {
        static Borders resolve(Block table, RenderOptions options) {
            String frame =
                    options.tableFrame().equals("auto")
                            ? table.format().frame()
                            : options.tableFrame();
            String grid =
                    options.tableGrid().equals("auto")
                            ? table.format().grid()
                            : options.tableGrid();
            return new Borders(
                    frame.equals("all") || frame.equals("sides"),
                    frame.equals("all") || frame.equals("ends"),
                    grid.equals("all") || grid.equals("rows"),
                    grid.equals("all") || grid.equals("cols"),
                    options.tableSectionBorders().equals("heavy"));
        }
    }

    private static boolean section(Block row) {
        return row.format().variant().equals("header") || row.format().variant().equals("footer");
    }

    private static String rowBackground(
            Block table, int row, RenderOptions options, Palette palette) {
        return section(table.children().get(row)) || options.tableStripes() && row % 2 == 1
                ? palette.stripe()
                : "";
    }

    private static boolean vertical(
            List<List<Cell>> grid, int row, int column, int count, Borders borders) {
        if (column == 0 || column == count) {
            return borders.sides();
        }
        return borders.columns()
                && (get(grid, row, column - 1) == null
                        || !get(grid, row, column - 1).equals(get(grid, row, column)));
    }

    private static void addRule(
            List<List<Span>> result,
            List<List<Cell>> grid,
            int[] widths,
            int boundary,
            int rowCount,
            Borders borders,
            boolean section,
            Palette palette,
            RenderOptions options) {
        boolean outer = boundary == 0 || boundary == rowCount;
        if (outer ? !borders.ends() : !borders.rows()) {
            return;
        }
        boolean[] segments = new boolean[widths.length];
        boolean any = false;
        for (int c = 0; c < widths.length; c++) {
            segments[c] =
                    outer
                            || get(grid, boundary - 1, c) == null
                            || !get(grid, boundary - 1, c).equals(get(grid, boundary, c));
            any |= segments[c];
        }
        if (!any) {
            return;
        }
        boolean heavy = section && borders.heavy();
        String horizontal = options.ascii() ? (heavy ? "=" : "-") : (heavy ? "━" : "─");
        var text = new StringBuilder();
        for (int c = 0; c <= widths.length; c++) {
            int mask =
                    (c > 0 && segments[c - 1] ? 1 : 0)
                            | (c < widths.length && segments[c] ? 2 : 0)
                            | (boundary > 0
                                            && vertical(
                                                    grid, boundary - 1, c, widths.length, borders)
                                    ? 4
                                    : 0)
                            | (boundary < rowCount
                                            && vertical(grid, boundary, c, widths.length, borders)
                                    ? 8
                                    : 0);
            String glyph =
                    options.ascii() ? asciiJunction(mask, horizontal) : junction(mask, heavy);
            text.append(glyph);
            if (c < widths.length) {
                text.append((segments[c] ? horizontal : " ").repeat(widths[c] + 2));
            }
        }
        result.add(List.of(new Span(text.toString(), palette.borderStyle())));
    }

    private static String asciiJunction(int mask, String horizontal) {
        boolean h = (mask & 3) != 0;
        boolean v = (mask & 12) != 0;
        return h && v ? "+" : h ? horizontal : v ? "|" : " ";
    }

    private static String junction(int mask, boolean heavy) {
        String[] thin = {
            " ", "─", "─", "─", "│", "┘", "└", "┴", "│", "┐", "┌", "┬", "│", "┤", "├", "┼"
        };
        String[] bold = {
            " ", "━", "━", "━", "│", "┙", "┕", "┷", "│", "┑", "┍", "┯", "│", "┥", "┝", "┿"
        };
        return (heavy ? bold : thin)[mask];
    }

    private static void collectChildren(
            Block block, List<Span> spans, Function<List<Span>, List<Span>> theme) {
        for (Block child : block.children()) {
            if (!spans.isEmpty()) {
                spans.add(new Span("\n"));
            }
            spans.addAll(theme.apply(child.spans()));
            collectChildren(child, spans, theme);
        }
    }

    private static void ensure(List<List<Cell>> grid, int row) {
        while (grid.size() <= row) {
            grid.add(new ArrayList<>());
        }
    }

    private static Cell get(List<List<Cell>> grid, int row, int column) {
        return row < grid.size() && column < grid.get(row).size()
                ? grid.get(row).get(column)
                : null;
    }
}

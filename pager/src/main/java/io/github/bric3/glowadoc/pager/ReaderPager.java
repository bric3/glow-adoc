// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.pager;

import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;

import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.highlight.DeferredHighlighter;
import io.github.bric3.glowadoc.render.ContentArea;
import io.github.bric3.glowadoc.render.Layout;
import io.github.bric3.glowadoc.render.Palette;
import io.github.bric3.glowadoc.render.RenderedLine;
import io.github.bric3.glowadoc.terminal.Ansi;
import io.github.bric3.glowadoc.terminal.KittyImages;
import io.github.bric3.glowadoc.terminal.Progress;
import io.github.bric3.glowadoc.terminal.TerminalSession;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class ReaderPager {
    private final Document document;
    private final Layout layout;
    private final DiagramRenderer diagrams;
    private final boolean colour;
    private final boolean truecolour;
    private final boolean images;
    private final int requestedWidth;
    private final boolean center;
    private final boolean pageBackground;
    private boolean mouse;
    private final String progressMode;
    private final boolean imageDim;
    private boolean hoverTracking;
    private int pointerX = -1;
    private int pointerY = -1;
    private int hoveredImage = -1;
    private List<KittyImages.Placement> visibleImages = List.of();
    private int contentMargin;
    private final Set<Integer> toggled = new HashSet<>();
    private int focused = -1;
    private boolean layoutDirty;
    private boolean anchorAssets;
    private List<RenderedLine> lines = List.of();

    private record ImageAsset(Path path, KittyImages.Size size) {}

    private final Map<Integer, ImageAsset> assets = new HashMap<>();
    private final Map<Integer, Block> diagramBlocks = new HashMap<>();
    private int top;
    private int left;
    private int width;
    private int height;
    private int retainedTop;
    private Appearance appearance;
    private boolean searching;
    private final Search search = new Search();
    private int searchOriginTop;
    private int searchOriginLeft;
    private String message = "";
    private TuiRunner activeRunner;
    private final DeferredHighlighter highlighter;
    private final Consumer<String> milestone;
    private boolean firstFrame = true;
    private boolean firstImage = true;
    private boolean highlightingReported;

    public ReaderPager(
            Document document,
            Layout layout,
            DiagramRenderer diagrams,
            boolean colour,
            boolean truecolour,
            boolean images,
            int requestedWidth,
            boolean center,
            boolean pageBackground,
            boolean mouse,
            String progressMode,
            boolean imageDim,
            DeferredHighlighter highlighter,
            Consumer<String> milestone) {
        this.document = document;
        this.layout = layout;
        this.diagrams = diagrams;
        this.colour = colour;
        this.truecolour = truecolour;
        this.images = images;
        this.requestedWidth = requestedWidth;
        this.center = center;
        this.pageBackground = pageBackground;
        this.mouse = mouse;
        this.progressMode = progressMode;
        this.imageDim = imageDim;
        this.highlighter = highlighter;
        this.milestone = milestone;
    }

    public void run(boolean automatic, Appearance fallback) throws Exception {
        try (var session = new TerminalSession(automatic, fallback)) {
            var config =
                    TuiConfig.builder()
                            .backend(session.backend())
                            .tickRate(Duration.ofMillis(100))
                            .mouseCapture(mouse)
                            .build();
            try (var runner = TuiRunner.create(config);
                    var kitty = new KittyImages(session.backend());
                    var progress = new Progress(session.backend(), progressMode, System.getenv())) {
                activeRunner = runner;
                session.start();
                runner.run(
                        (event, tui) -> {
                            session.tick();
                            boolean redraw = session.takeDirty() || layoutDirty;
                            if (diagrams.takeDirty()) {
                                layoutDirty = true;
                                anchorAssets = true;
                                redraw = true;
                            }
                            for (String diagnostic : diagrams.takeDiagnostics()) {
                                message = diagnostic;
                                redraw = true;
                            }
                            for (int id : session.takeMissingImages()) {
                                kitty.invalidate(id);
                            }
                            if (highlighter != null && highlighter.takeDirty()) {
                                layoutDirty = true;
                                redraw = true;
                                if (!highlightingReported && highlighter.isIdle()) {
                                    highlightingReported = true;
                                    milestone.accept("highlighting-ready");
                                }
                            }
                            if (event instanceof KeyEvent key) {
                                if (key.isCtrlC() || (!searching && key.isChar('q'))) {
                                    tui.quit();
                                    return false;
                                }
                                if (searching) {
                                    if (key.isKey(KeyCode.ENTER)) {
                                        searching = false;
                                    } else if (key.isKey(KeyCode.ESCAPE)) {
                                        searching = false;
                                        updateSearch("");
                                    } else if (key.isKey(KeyCode.BACKSPACE)
                                            && !search.query().isEmpty()) {
                                        String query = search.query();
                                        updateSearch(
                                                query.substring(
                                                        0,
                                                        query.offsetByCodePoints(
                                                                query.length(), -1)));
                                    } else if (key.code() == KeyCode.CHAR && !key.hasCtrl()) {
                                        updateSearch(search.query() + key.string());
                                    }
                                } else if (key.isFocusNext() || key.isFocusPrevious()) {
                                    focus(key.isFocusPrevious() ? -1 : 1);
                                } else if (key.isKey(KeyCode.ENTER) && focused >= 0) {
                                    toggle(focused);
                                } else if (key.isUp() || key.isChar('k')) {
                                    top--;
                                } else if (key.isDown() || key.isChar('j')) {
                                    top++;
                                } else if (key.isPageDown() || key.isChar(' ')) {
                                    top += height;
                                } else if (key.isPageUp() || key.isChar('b')) {
                                    top -= height;
                                } else if (key.isChar('g') || key.isKey(KeyCode.HOME)) {
                                    top = 0;
                                } else if (key.isChar('G') || key.isKey(KeyCode.END)) {
                                    top = lines.size();
                                } else if (key.isLeft()) {
                                    left = Math.max(0, left - 4);
                                } else if (key.isRight()) {
                                    left += 4;
                                } else if (key.isChar('/')) {
                                    searching = true;
                                    searchOriginTop = top;
                                    searchOriginLeft = left;
                                    updateSearch("");
                                } else if (key.isChar('n')) {
                                    find(1);
                                } else if (key.isChar('N')) {
                                    find(-1);
                                } else if (key.isChar('r')) {
                                    session.refresh();
                                } else if (key.isChar('m')) {
                                    try {
                                        if (mouse) {
                                            session.backend().disableMouseCapture();
                                        } else {
                                            session.backend().enableMouseCapture(hoverTracking);
                                        }
                                        mouse = !mouse;
                                        pointerX = pointerY = -1;
                                    } catch (java.io.IOException e) {
                                        message = "Mouse capture unavailable";
                                    }
                                }
                                redraw = true;
                            } else if (mouse && event instanceof MouseEvent pointer) {
                                pointerX = pointer.x();
                                pointerY = pointer.y();
                                int hovered = hoveredImage(visibleImages, pointerX, pointerY);
                                if (hoverTracking && hovered != hoveredImage) {
                                    hoveredImage = hovered;
                                    redraw = true;
                                }
                                if (pointer.kind() == MouseEventKind.SCROLL_UP) {
                                    top -= 3;
                                    redraw = true;
                                } else if (pointer.kind() == MouseEventKind.SCROLL_DOWN) {
                                    top += 3;
                                    redraw = true;
                                } else if (pointer.isPress()
                                        && pointer.isLeftButton()
                                        && pointer.y() >= 0
                                        && pointer.y() < height
                                        && pointer.x() >= contentMargin
                                        && pointer.x() < contentMargin + width
                                        && top + pointer.y() < lines.size()) {
                                    int disclosure = lines.get(top + pointer.y()).disclosure();
                                    if (disclosure >= 0) {
                                        focused = disclosure;
                                        toggle(disclosure);
                                        redraw = true;
                                    }
                                }
                            }
                            top = clampTop(top);
                            return redraw;
                        },
                        frame -> draw(frame, session, kitty, progress));
            }
        }
    }

    private void draw(Frame frame, TerminalSession session, KittyImages kitty, Progress progress) {
        int available = Math.max(1, frame.width() - 1);
        var area = ContentArea.within(frame.width(), requestedWidth, center);
        int nextWidth = area.width();
        if (height != Math.max(1, frame.height() - 1)) {
            kitty.refreshPlacements();
        }
        height = Math.max(1, frame.height() - 1);
        boolean rebuilt = false;
        if (layoutDirty
                || width != nextWidth
                || appearance != session.appearance()
                || lines.isEmpty()) {
            boolean preserveViewport = (layoutDirty || width == nextWidth) && !anchorAssets;
            int previousTop = top;
            layoutDirty = false;
            int anchor =
                    lines.isEmpty() ? 0 : lines.get(Math.min(top, lines.size() - 1)).blockIndex();
            int anchorStart = Math.min(top, Math.max(0, lines.size() - 1));
            while (anchorStart > 0 && lines.get(anchorStart - 1).blockIndex() == anchor) {
                anchorStart--;
            }
            int anchorOffset = Math.max(0, top - anchorStart);
            width = nextWidth;
            appearance = session.appearance();
            assets.clear();
            diagramBlocks.clear();
            lines = new ArrayList<>(layout.render(document, width, appearance, toggled, true));
            if (lines.stream().noneMatch(line -> line.disclosure() == focused)) {
                focused = -1;
            }
            if (images && session.kitty()) {
                prepareImages(session);
            }
            top = 0;
            while (top < lines.size() && lines.get(top).blockIndex() < anchor) {
                top++;
            }
            if (anchorAssets && top < lines.size() && lines.get(top).blockIndex() == anchor) {
                int end = top + 1;
                while (end < lines.size() && lines.get(end).blockIndex() == anchor) {
                    end++;
                }
                top += Math.min(anchorOffset, end - top - 1);
            }
            anchorAssets = false;
            // Leave blank space below a folded block rather than moving its title.
            retainedTop = preserveViewport ? previousTop : 0;
            if (preserveViewport) {
                top = previousTop;
            }
            rebuilt = true;
        } else if (images && session.kitty() && assets.isEmpty()) {
            rebuilt = prepareImages(session);
        }
        top = clampTop(top);
        boolean tracking = imageDim && images && session.kitty() && appearance == Appearance.DARK;
        if (tracking != hoverTracking) {
            hoverTracking = tracking;
            if (mouse) {
                try {
                    session.backend().disableMouseCapture();
                    session.backend().enableMouseCapture(tracking);
                } catch (java.io.IOException e) {
                    message = "Mouse motion unavailable";
                }
            }
        }
        if (rebuilt) {
            kitty.refreshPlacements();
            search.update(search.query(), lines, top, left);
            if (searching) {
                searchOriginTop = top;
                searchOriginLeft = left;
            }
        }
        var palette = Palette.forAppearance(appearance);
        var base = dev.tamboui.style.Style.EMPTY;
        if (colour) {
            base =
                    base.fg(
                            Color.hex(
                                    session.foreground().isEmpty()
                                            ? (appearance == Appearance.DARK
                                                    ? "#eeeeee"
                                                    : "#202020")
                                            : session.foreground()));
            if (pageBackground) {
                base =
                        base.bg(
                                Color.hex(
                                        session.background().isEmpty()
                                                ? Palette.forAppearance(appearance).canvas()
                                                : session.background()));
            }
        }
        frame.buffer().setStyle(frame.area(), base);
        int margin = area.margin();
        contentMargin = margin;
        for (int y = 0; y < height && top + y < lines.size(); y++) {
            var row = lines.get(top + y);
            var spans = new ArrayList<dev.tamboui.text.Span>();
            int skipped = 0;
            for (var part : search.parts(top + y, row.spans())) {
                var span = part.span();
                String text = span.text();
                int cells = CharWidth.of(text);
                if (skipped + cells <= left) {
                    skipped += cells;
                    continue;
                }
                if (skipped < left) {
                    int offset = 0;
                    while (offset < text.length() && skipped < left) {
                        int cp = text.codePointAt(offset);
                        skipped += CharWidth.of(cp);
                        offset += Character.charCount(cp);
                    }
                    text = text.substring(offset);
                }
                var style = base.patch(Ansi.style(span.style(), colour, truecolour));
                if (row.disclosure() == focused && focused >= 0) {
                    style = style.reversed();
                }
                if (part.matched()) {
                    style =
                            colour
                                    ? style.patch(
                                            Ansi.style(
                                                    Style.PLAIN.colours(
                                                            part.active()
                                                                    ? palette.canvas()
                                                                    : palette.accent(),
                                                            part.active()
                                                                    ? palette.accent()
                                                                    : palette.stripe()),
                                                    true,
                                                    truecolour))
                                    : style.reversed();
                    if (part.active()) {
                        style = style.bold().underlined();
                    }
                }
                spans.add(dev.tamboui.text.Span.styled(text, style));
            }
            frame.buffer().setLine(margin, y, Line.from(spans), available - margin);
        }
        String matches =
                search.query().isEmpty()
                        ? ""
                        : search.count() == 0
                                ? "  No match"
                                : "  " + search.ordinal() + "/" + search.count() + " matches";
        String status =
                searching
                        ? "/" + search.query() + matches
                        : "q quit  / search  Tab/Enter fold  "
                                + (mouse ? "m select  " : "m mouse  ")
                                + matches
                                + (message.isEmpty() ? "" : "  " + message);
        var statusStyle =
                colour ? Ansi.style(palette.statusStyle(), true, truecolour) : base.reversed();
        String positionText = " " + (top + 1) + "/" + lines.size() + " ";
        int positionWidth = Math.min(available, CharWidth.of(positionText));
        String controls = clip(status, available - positionWidth);
        var positionStyle =
                colour
                        ? Ansi.style(
                                Style.PLAIN.colours(palette.surface(), palette.accent()),
                                true,
                                truecolour)
                        : statusStyle.bold();
        var statusSpans =
                List.of(
                        dev.tamboui.text.Span.styled(
                                controls
                                        + " "
                                                .repeat(
                                                        Math.max(
                                                                0,
                                                                available
                                                                        - positionWidth
                                                                        - CharWidth.of(controls))),
                                statusStyle),
                        dev.tamboui.text.Span.styled(
                                clip(positionText, positionWidth), positionStyle));
        frame.buffer().setLine(0, frame.height() - 1, Line.from(statusSpans), available);
        // Graphics are emitted after TamboUI has flushed its text frame.
        pendingImages =
                () -> {
                    var placements = new ArrayList<KittyImages.Placement>();
                    for (var entry : assets.entrySet()) {
                        int first = entry.getKey();
                        var asset = entry.getValue();
                        int count = asset.size().rows();
                        int start = Math.max(top, first);
                        int end = Math.min(top + height, first + count);
                        if (start < end) {
                            placements.add(
                                    new KittyImages.Placement(
                                            first + 1,
                                            asset.path(),
                                            start - top,
                                            margin,
                                            asset.size().columns(),
                                            end - start,
                                            start - first,
                                            count));
                        }
                    }
                    visibleImages = List.copyOf(placements);
                    hoveredImage =
                            session.focused()
                                    ? hoveredImage(visibleImages, pointerX, pointerY)
                                    : -1;
                    if (hoverTracking && mouse) {
                        placements.replaceAll(placement ->
                                new KittyImages.Placement(
                                        placement.id(),
                                        placement.path(),
                                        placement.row(),
                                        placement.column(),
                                        placement.columns(),
                                        placement.rows(),
                                        placement.cropRows(),
                                        placement.totalRows(),
                                        placement.id() != hoveredImage));
                    }
                    kitty.render(placements);
                    if (firstImage && !placements.isEmpty()) {
                        firstImage = false;
                        milestone.accept("first-image");
                    }
                };
        activeRunner.runLater(() -> {
            if (firstFrame) {
                firstFrame = false;
                milestone.accept("first-frame");
                if (highlighter != null) {
                    highlighter.start();
                }
            }
            try {
                progress.update(top, lines.size(), height);
            } catch (java.io.IOException e) {
                message = "Terminal progress unavailable";
            }
            if (!images || !session.kitty()) {
                return;
            }
            requestVisibleDiagrams();
            try {
                pendingImages.draw();
            } catch (java.io.IOException e) {
                message = "Image unavailable";
            }
        });
    }

    @FunctionalInterface
    private interface ImageDraw {
        void draw() throws java.io.IOException;
    }

    private ImageDraw pendingImages = () -> {};

    static int hoveredImage(List<KittyImages.Placement> placements, int x, int y) {
        for (var placement : placements) {
            if (x >= placement.column()
                    && x < placement.column() + placement.columns()
                    && y >= placement.row()
                    && y < placement.row() + placement.rows()) {
                return placement.id();
            }
        }
        return -1;
    }

    private void requestVisibleDiagrams() {
        int previous = -1;
        for (int i = top; i < Math.min(lines.size(), top + height); i++) {
            int position = lines.get(i).blockIndex();
            if (position == previous) {
                continue;
            }
            previous = position;
            Block block = diagramBlocks.get(position);
            if (block != null) {
                diagrams.request(block, appearance);
            }
        }
    }

    private boolean prepareImages(TerminalSession session) {
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            var row = lines.get(i);
            if (row.asset() == null) {
                continue;
            }
            var block = row.asset();
            if (block.kind() == Block.Kind.DIAGRAM) {
                diagramBlocks.put(row.blockIndex(), block);
            }
            Optional<Path> path =
                    block.kind() == Block.Kind.DIAGRAM
                            ? firstFrame ? Optional.empty() : diagrams.cached(block, appearance)
                            : DiagramRenderer.localImage(document, block.target());
            if (path.isEmpty()) {
                continue;
            }
            KittyImages.Size size;
            try {
                size =
                        KittyImages.measure(
                                path.get(), width, session.cellWidth(), session.cellHeight());
            } catch (java.io.IOException e) {
                message = "Image unavailable";
                continue;
            }
            assets.put(i, new ImageAsset(path.get(), size));
            changed = true;
            int end = i + 1;
            while (end < lines.size() && lines.get(end).blockIndex() == row.blockIndex()) {
                end++;
            }
            lines.subList(i, end).clear();
            for (int j = 0; j < size.rows(); j++) {
                lines.add(i + j, new RenderedLine(List.of(), row.blockIndex(), null));
            }
            i += size.rows() - 1;
        }
        return changed;
    }

    private void updateSearch(String query) {
        search.update(query, lines, searchOriginTop, searchOriginLeft);
        top = searchOriginTop;
        left = searchOriginLeft;
        revealMatch();
    }

    private void find(int direction) {
        search.next(direction);
        revealMatch();
    }

    private void revealMatch() {
        var match = search.active();
        if (match == null) {
            return;
        }
        top = match.line();
        if (match.column() < left) {
            left = match.column();
        } else if (match.endColumn() > left + width) {
            left = Math.max(0, Math.min(match.column(), match.endColumn() - width));
        }
    }

    private void focus(int direction) {
        var headers =
                lines.stream()
                        .filter(line -> line.disclosure() >= 0)
                        .map(RenderedLine::disclosure)
                        .distinct()
                        .toList();
        if (headers.isEmpty()) {
            return;
        }
        int current = headers.indexOf(focused);
        focused =
                headers.get(
                        Math.floorMod(
                                current < 0
                                        ? (direction > 0 ? 0 : headers.size() - 1)
                                        : current + direction,
                                headers.size()));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).disclosure() == focused) {
                if (i < top || i >= top + height) {
                    top = i;
                }
                break;
            }
        }
    }

    private void toggle(int disclosure) {
        if (!toggled.add(disclosure)) {
            toggled.remove(disclosure);
        }
        layoutDirty = true;
        anchorAssets = false;
    }

    private int clampTop(int requested) {
        return Math.clamp(requested, 0, Math.max(retainedTop, Math.max(0, lines.size() - height)));
    }

    private static String clip(String text, int width) {
        var out = new StringBuilder();
        for (int cp : text.codePoints().toArray()) {
            if (CharWidth.of(out.toString()) + CharWidth.of(cp) > width) {
                break;
            }
            out.appendCodePoint(cp);
        }
        return out.toString();
    }
}

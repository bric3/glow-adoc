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

import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.render.Layout;
import io.github.bric3.glowadoc.render.RenderedLine;
import io.github.bric3.glowadoc.terminal.Ansi;
import io.github.bric3.glowadoc.terminal.KittyImages;
import io.github.bric3.glowadoc.terminal.TerminalSession;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ReaderPager {
    private final Document document;
    private final Layout layout;
    private final DiagramRenderer diagrams;
    private final boolean colour;
    private final boolean truecolour;
    private final boolean images;
    private final int requestedWidth;
    private List<RenderedLine> lines = List.of();

    private record ImageAsset(Path path, KittyImages.Size size) {}

    private final Map<Integer, ImageAsset> assets = new HashMap<>();
    private int top;
    private int left;
    private int width;
    private int height;
    private Appearance appearance;
    private boolean searching;
    private String search = "";
    private String message = "";
    private TuiRunner activeRunner;

    public ReaderPager(
            Document document,
            Layout layout,
            DiagramRenderer diagrams,
            boolean colour,
            boolean truecolour,
            boolean images,
            int requestedWidth) {
        this.document = document;
        this.layout = layout;
        this.diagrams = diagrams;
        this.colour = colour;
        this.truecolour = truecolour;
        this.images = images;
        this.requestedWidth = requestedWidth;
    }

    public void run(boolean automatic, Appearance fallback) throws Exception {
        try (var session = new TerminalSession(automatic, fallback)) {
            var config =
                    TuiConfig.builder()
                            .backend(session.backend())
                            .tickRate(Duration.ofMillis(100))
                            .mouseCapture(false)
                            .build();
            try (var runner = TuiRunner.create(config);
                    var kitty = new KittyImages(session.backend())) {
                activeRunner = runner;
                session.start();
                runner.run(
                        (event, tui) -> {
                            session.tick();
                            boolean redraw = session.takeDirty();
                            if (event instanceof KeyEvent key) {
                                if (key.isCtrlC() || (!searching && key.isChar('q'))) {
                                    tui.quit();
                                    return false;
                                }
                                if (searching) {
                                    if (key.isKey(KeyCode.ENTER)) {
                                        searching = false;
                                        find(1);
                                    } else if (key.isKey(KeyCode.ESCAPE)) {
                                        searching = false;
                                    } else if (key.isKey(KeyCode.BACKSPACE) && !search.isEmpty()) {
                                        search =
                                                search.substring(
                                                        0,
                                                        search.offsetByCodePoints(
                                                                search.length(), -1));
                                    } else if (key.code() == KeyCode.CHAR && !key.hasCtrl()) {
                                        search += key.string();
                                    }
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
                                    search = "";
                                } else if (key.isChar('n')) {
                                    find(1);
                                } else if (key.isChar('N')) {
                                    find(-1);
                                } else if (key.isChar('r')) {
                                    session.refresh();
                                }
                                redraw = true;
                            }
                            top = Math.clamp(top, 0, Math.max(0, lines.size() - height));
                            return redraw;
                        },
                        frame -> draw(frame, session, kitty));
            }
        }
    }

    private void draw(Frame frame, TerminalSession session, KittyImages kitty) {
        int available = Math.max(1, frame.width());
        int nextWidth = requestedWidth > 0 ? Math.min(requestedWidth, available) : available;
        height = Math.max(1, frame.height() - 1);
        if (width != nextWidth || appearance != session.appearance() || lines.isEmpty()) {
            int anchor =
                    lines.isEmpty() ? 0 : lines.get(Math.min(top, lines.size() - 1)).blockIndex();
            width = nextWidth;
            appearance = session.appearance();
            assets.clear();
            lines = new ArrayList<>(layout.render(document, width, appearance));
            if (images && session.kitty()) {
                prepareImages(session);
            }
            top = 0;
            while (top < lines.size() && lines.get(top).blockIndex() < anchor) {
                top++;
            }
        } else if (images && session.kitty() && assets.isEmpty()) {
            prepareImages(session);
        }
        top = Math.clamp(top, 0, Math.max(0, lines.size() - height));
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
            if (!session.background().isEmpty()) {
                base = base.bg(Color.hex(session.background()));
            }
        }
        frame.buffer().setStyle(frame.area(), base);
        for (int y = 0; y < height && top + y < lines.size(); y++) {
            var row = lines.get(top + y);
            var spans = new ArrayList<dev.tamboui.text.Span>();
            int skipped = 0;
            for (var span : row.spans()) {
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
                spans.add(
                        dev.tamboui.text.Span.styled(
                                text, base.patch(Ansi.style(span.style(), colour, truecolour))));
            }
            frame.buffer().setLine(0, y, Line.from(spans), available);
        }
        String status =
                searching
                        ? "/" + search
                        : "q quit  / search  n/N next  r refresh  "
                                + (top + 1)
                                + "/"
                                + lines.size()
                                + (message.isEmpty() ? "" : "  " + message);
        frame.buffer().setLine(0, frame.height() - 1, Line.from(status), available);
        // Graphics are emitted after TamboUI has flushed its text frame.
        pendingImages =
                () -> {
                    kitty.clear();
                    for (var entry : assets.entrySet()) {
                        int first = entry.getKey();
                        var asset = entry.getValue();
                        int count = asset.size().rows();
                        int start = Math.max(top, first);
                        int end = Math.min(top + height, first + count);
                        if (start < end) {
                            try {
                                kitty.show(
                                        asset.path(),
                                        start - top,
                                        asset.size().columns(),
                                        end - start,
                                        start - first,
                                        count);
                            } catch (java.io.IOException e) {
                                message = "Image unavailable";
                            }
                        }
                    }
                };
        if (images && session.kitty()) {
            activeRunner.runLater(
                    () -> {
                        try {
                            pendingImages.draw();
                        } catch (java.io.IOException e) {
                            message = "Image unavailable";
                        }
                    });
        }
    }

    @FunctionalInterface
    private interface ImageDraw {
        void draw() throws java.io.IOException;
    }

    private ImageDraw pendingImages = () -> {};

    private void prepareImages(TerminalSession session) {
        for (int i = 0; i < lines.size(); i++) {
            var row = lines.get(i);
            if (row.asset() == null) {
                continue;
            }
            var block = row.asset();
            Optional<Path> path =
                    block.kind() == Block.Kind.DIAGRAM
                            ? diagrams.render(block, appearance)
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
            int end = i + 1;
            if (block.kind() == Block.Kind.DIAGRAM) {
                while (end < lines.size() && lines.get(end).blockIndex() == row.blockIndex()) {
                    end++;
                }
            }
            lines.subList(i, end).clear();
            for (int j = 0; j < size.rows(); j++) {
                lines.add(i + j, new RenderedLine(List.of(), row.blockIndex(), null));
            }
            i += size.rows() - 1;
        }
    }

    private void find(int direction) {
        if (search.isEmpty() || lines.isEmpty()) {
            return;
        }
        for (int step = 1; step <= lines.size(); step++) {
            int index = Math.floorMod(top + direction * step, lines.size());
            if (lines.get(index)
                    .text()
                    .toLowerCase(Locale.ROOT)
                    .contains(search.toLowerCase(Locale.ROOT))) {
                top = index;
                return;
            }
        }
        message = "No match";
    }
}

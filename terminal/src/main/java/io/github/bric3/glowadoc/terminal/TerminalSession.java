// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.terminal;

import dev.tamboui.backend.aesh.AeshBackend;

import io.github.bric3.glowadoc.document.Appearance;

import org.aesh.terminal.tty.TerminalBuilder;
import org.aesh.terminal.tty.TerminalConnection;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class TerminalSession implements AutoCloseable {
    private final TerminalConnection connection;
    private final AeshBackend backend;
    private final ReplyFilter filter;
    private final Map<Integer, String> colours = new ConcurrentHashMap<>();
    private volatile Appearance appearance = Appearance.DARK;
    private volatile boolean dirty = true;
    private volatile boolean kitty;
    private volatile boolean notifications;
    private boolean enabledNotifications;
    private volatile long lastReply;
    private long nextPoll;
    private long deadline;
    private int misses;
    private final boolean automatic;
    private volatile int cellWidth = 8;
    private volatile int cellHeight = 16;

    public TerminalSession(boolean automatic, Appearance fallback) throws IOException {
        this.automatic = automatic;
        appearance = fallback;
        connection =
                new TerminalConnection(
                        TerminalBuilder.builder().system(true).nativeSignals(true).build());
        backend = new AeshBackend(connection);
        var keys = connection.stdinHandler();
        filter = new ReplyFilter(this::reply, keys);
        connection.setStdinHandler(filter::accept);
        connection.setThemeChangeHandler(
                theme -> {
                    lastReply = System.nanoTime();
                    if (automatic) {
                        Appearance next =
                                theme.toString().equalsIgnoreCase("light")
                                        ? Appearance.LIGHT
                                        : Appearance.DARK;
                        if (appearance != next) {
                            appearance = next;
                            dirty = true;
                        }
                        if (System.nanoTime() > deadline) {
                            refresh();
                        }
                    }
                });
        connection.setFocusHandler(
                focused -> {
                    if (focused) {
                        refresh();
                    }
                });
    }

    public AeshBackend backend() {
        return backend;
    }

    public Appearance appearance() {
        return appearance;
    }

    public boolean kitty() {
        return kitty;
    }

    public int cellWidth() {
        return cellWidth;
    }

    public int cellHeight() {
        return cellHeight;
    }

    public boolean takeDirty() {
        boolean changed = dirty;
        dirty = false;
        return changed;
    }

    public String foreground() {
        return colours.getOrDefault(10, "");
    }

    public String background() {
        return colours.getOrDefault(11, "");
    }

    public void start() {
        connection.write(
                "\u001b[16t\u001b[?2031$p\u001b_Gi=2147483000,s=1,v=1,a=q,t=d,f=24;AAAA\u001b\\");
        refresh();
    }

    public void refresh() {
        misses = 0;
        nextPoll = 0;
    }

    public void tick() {
        filter.expire();
        long now = System.nanoTime();
        if (deadline != 0 && now > deadline) {
            if (lastReply < deadline - 200_000_000L) {
                misses++;
            }
            deadline = 0;
        }
        if (automatic && now >= nextPoll && misses < 2 && (!notifications || nextPoll == 0)) {
            connection.write(
                    "\u001b[?996n\u001b]10;?\u001b\\\u001b]11;?\u001b\\\u001b]4;0;?;7;?;8;?;15;?\u001b\\");
            deadline = now + 200_000_000L;
            nextPoll = now + 2_000_000_000L;
        }
    }

    private void reply(String value) {
        var cell = Pattern.compile("\u001b\\[6;([0-9]{1,4});([0-9]{1,4})t").matcher(value);
        if (cell.matches()) {
            cellHeight = Math.max(1, Integer.parseInt(cell.group(1)));
            cellWidth = Math.max(1, Integer.parseInt(cell.group(2)));
            dirty = true;
        }
        if (value.contains("i=2147483000") && value.contains(";OK")) {
            kitty = true;
            dirty = true;
        }
        if (value.matches("\u001b\\[\\?2031;[12]\\$y")) {
            notifications = true;
            if (value.contains(";2$y")) {
                connection.write("\u001b[?2031h");
                enabledNotifications = true;
            }
        }
        if (value.startsWith("\u001b[?997") && automatic) {
            if (value.contains(";1n")) {
                appearance = Appearance.DARK;
            }
            if (value.contains(";2n")) {
                appearance = Appearance.LIGHT;
            }
            dirty = true;
            refresh();
        }
        var matcher =
                Pattern.compile(
                                "\u001b](10|11|4);(?:(\\d+);)?rgb:([0-9a-fA-F]{1,4})/([0-9a-fA-F]{1,4})/([0-9a-fA-F]{1,4})")
                        .matcher(value);
        if (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            int r = channel(matcher.group(3));
            int g = channel(matcher.group(4));
            int b = channel(matcher.group(5));
            String hex = String.format("#%02x%02x%02x", r, g, b);
            if (index == 4 && matcher.group(2) == null) {
                return;
            }
            int key = index == 4 ? 256 + Integer.parseInt(matcher.group(2)) : index;
            if (!hex.equals(colours.put(key, hex))) {
                dirty = true;
            }
            if (index == 11 && automatic) {
                appearance =
                        0.2126 * r + 0.7152 * g + 0.0722 * b > 140
                                ? Appearance.LIGHT
                                : Appearance.DARK;
            }
            lastReply = System.nanoTime();
        }
    }

    public static int channel(String hex) {
        return (int)
                Math.round(Integer.parseInt(hex, 16) * 255.0 / ((1L << (hex.length() * 4)) - 1));
    }

    @Override
    public void close() throws IOException {
        try {
            if (enabledNotifications) {
                connection.write("\u001b[?2031l");
            }
            backend.close();
        } finally {
            connection.close();
        }
    }
}

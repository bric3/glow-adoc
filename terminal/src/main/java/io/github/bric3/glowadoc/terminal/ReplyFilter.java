// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.terminal;

import java.util.function.Consumer;

/** Removes terminal replies before the pager interprets keyboard input. */
public final class ReplyFilter {
    private final Consumer<String> reply;
    private final Consumer<int[]> keys;
    private final StringBuilder pending = new StringBuilder();
    private long started;

    public ReplyFilter(Consumer<String> reply, Consumer<int[]> keys) {
        this.reply = reply;
        this.keys = keys;
    }

    public synchronized void accept(int[] input) {
        for (int cp : input) {
            if (pending.isEmpty()) {
                if (cp != 27) {
                    keys.accept(new int[] {cp});
                    continue;
                }
                started = System.nanoTime();
            }
            pending.appendCodePoint(cp);
            String value = pending.toString();
            boolean csi = value.startsWith("\u001b[");
            boolean string = value.startsWith("\u001b]") || value.startsWith("\u001b_");
            if ((csi && value.length() > 2 && cp >= 64 && cp <= 126)
                    || (string && (cp == 7 || value.endsWith("\u001b\\")))
                    || (value.length() == 2 && !csi && !string)
                    || value.length() > 4096) {
                if (value.startsWith("\u001b]")
                        || value.startsWith("\u001b_G")
                        || value.matches("\u001b\\[6;[0-9]+;[0-9]+t")
                        || value.matches("\u001b\\[\\?2031;[0-4]\\$y")
                        || value.matches("\u001b\\[\\?997(?:;[12])?n")) {
                    reply.accept(value);
                } else {
                    keys.accept(value.codePoints().toArray());
                }
                pending.setLength(0);
            }
        }
    }

    public synchronized void expire() {
        if (!pending.isEmpty() && System.nanoTime() - started > 200_000_000L) {
            if (!pending.toString().startsWith("\u001b]")
                    && !pending.toString().startsWith("\u001b_")) {
                keys.accept(pending.toString().codePoints().toArray());
            }
            pending.setLength(0);
        }
    }
}

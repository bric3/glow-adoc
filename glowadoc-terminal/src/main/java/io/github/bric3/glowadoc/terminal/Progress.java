// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import dev.tamboui.terminal.Backend;
import java.io.IOException;
import java.util.Map;

/**
 * Session-owned OSC 9;4 reading progress.
 */
public final class Progress implements AutoCloseable {
    private final Backend backend;
    private final boolean enabled;
    private int previous = -1;

    public Progress(Backend backend, String mode, Map<String, String> environment) {
        this.backend = backend;
        enabled = mode.equals("always") || mode.equals("auto") && supported(environment);
    }

    static boolean supported(Map<String, String> env) {
        if (env.containsKey("TMUX") || env.containsKey("STY")) {
            return false;
        }
        return switch (env.getOrDefault("TERM_PROGRAM", "")) {
            case "ghostty", "iTerm.app", "WezTerm" -> true;
            default -> env.containsKey("WT_SESSION") || env.containsKey("KONSOLE_VERSION");
        };
    }

    public void update(int top, int rows, int height) throws IOException {
        int maximum = Math.max(0, rows - height);
        int percentage = maximum == 0 ? 100 : (int) (Math.clamp((long) top, 0, maximum) * 100 / maximum);
        if (enabled && previous != percentage) {
            backend.writeRaw("]9;4;1;" + percentage + "");
            backend.flush();
            previous = percentage;
        }
    }

    @Override
    public void close() throws IOException {
        if (enabled && previous >= 0) {
            backend.writeRaw("]9;4;0");
            backend.flush();
        }
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.*;
import dev.tamboui.terminal.Backend;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.Map;

class ProgressTest {
    private final StringBuilder output = new StringBuilder();
    private final Backend backend = (Backend) Proxy.newProxyInstance(Backend.class.getClassLoader(), new Class<?>[] {
            Backend.class
    }, (proxy, method, args) -> {
        if (method.getName().equals("writeRaw")) {
            output.append(args[0]);
        }
        return null;
    });

    @Test
    void reportsPositionOnceAndClearsOnExit() throws Exception {
        try (var progress = new Progress(backend, "always", Map.of())) {
            progress.update(0, 120, 20);
            progress.update(50, 120, 20);
            progress.update(50, 120, 20);
            progress.update(200, 120, 20);
        }
        assertEquals(
                "\u001B]9;4;1;0\u0007\u001B]9;4;1;50\u0007\u001B]9;4;1;100\u0007\u001B]9;4;0\u0007",
                output.toString()
        );
    }

    @Test
    void disablesUnknownTerminalsAndMultiplexersUnlessForced() throws Exception {
        assertTrue(Progress.supported(Map.of("TERM_PROGRAM", "ghostty")));
        assertTrue(Progress.supported(Map.of("TERM_PROGRAM", "iTerm.app")));
        assertFalse(Progress.supported(Map.of("TERM_PROGRAM", "ghostty", "TMUX", "session")));
        for (String mode : new String[] {"auto", "never"}) {
            try (var progress = new Progress(backend, mode, Map.of())) {
                progress.update(0, 0, 20);
            }
        }
        assertEquals("", output.toString());
    }
}

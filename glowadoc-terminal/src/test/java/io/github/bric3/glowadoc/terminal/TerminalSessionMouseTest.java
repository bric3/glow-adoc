// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.aesh.terminal.tty.MouseEvent;
import org.junit.jupiter.api.Test;

class TerminalSessionMouseTest {
    @Test
    void preservesMouseCoordinatesButtonsAndModifiersForBackendDecoding() {
        for (int button : new int[] {0, 1, 2, 4, 8, 16, 32, 35, 64, 65}) {
            var event = MouseEvent.parseSgr('M', new int[] {button, 40, 2}, 3);
            assertEquals("\u001B[<" + button + ";40;2M", TerminalSession.mouseSequence(event));
        }
        var released = MouseEvent.parseSgr('m', new int[] {0, 40, 2}, 3);
        assertEquals("\u001B[<0;40;2m", TerminalSession.mouseSequence(released));
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Bounds image output and subprocess diagnostics while streaming.
 */
public final class LimitedOutputStream extends FilterOutputStream {
    private final long maximum;
    private long written;

    public LimitedOutputStream(OutputStream output, long maximum) {
        super(output);
        this.maximum = maximum;
    }

    @Override
    public void write(int b) throws IOException {
        if (++written > maximum) {
            throw new IOException("Renderer output limit exceeded");
        }
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        written += len;
        if (written > maximum) {
            throw new IOException("Renderer output limit exceeded");
        }
        out.write(b, off, len);
    }
}

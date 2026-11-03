// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams;

import io.github.bric3.glowadoc.document.Appearance;
import java.nio.file.Path;

/**
 * A PNG renderer whose workers and subprocesses stop when it closes.
 */
@FunctionalInterface
public interface DiagramBackend extends AutoCloseable {
    /**
     * Scale maps logical image pixels to raster pixels for the terminal's cell density.
     */
    void render(String source, Path output, Appearance appearance, double scale) throws Exception;

    @Override
    default void close() {}
}

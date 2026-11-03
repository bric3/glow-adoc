// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams.ditaa;

import io.github.bric3.glowadoc.diagrams.DiagramBackend;
import io.github.bric3.glowadoc.diagrams.LimitedOutputStream;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.traceur.ditaa.DitaaRenderer;
import io.github.bric3.traceur.ditaa.RenderOptions;
import io.github.bric3.traceur.ditaa.RenderTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DitaaBackend implements DiagramBackend {
    @Override
    public void render(String source, Path output, Appearance appearance, double scale) throws IOException {
        try (var out = new LimitedOutputStream(Files.newOutputStream(output), 10_000_000)) {
            new DitaaRenderer().render(source, new RenderOptions(scale, true, true), RenderTarget.png(out));
        }
    }
}

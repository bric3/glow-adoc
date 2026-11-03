// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams.plantuml;

import io.github.bric3.glowadoc.diagrams.DiagramBackend;
import io.github.bric3.glowadoc.diagrams.LimitedOutputStream;
import io.github.bric3.glowadoc.document.Appearance;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.AbstractDiagram;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PlantUmlBackend implements DiagramBackend {
    public PlantUmlBackend() {
        System.setProperty("PLANTUML_SECURITY_PROFILE", "SANDBOX");
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void render(String source, Path output, Appearance appearance, double scale) throws IOException {
        if (source.matches("(?is).*![a-z]*(?:include|import|theme).*")) {
            throw new IOException("PlantUML includes, imports and themes are disabled");
        }
        if (!source.stripLeading().startsWith("@start")) {
            source = "@startuml\n" + source + "\n@enduml";
        }
        int newline = source.indexOf('\n');
        if (newline < 0) {
            throw new IOException("PlantUML requires a multiline diagram");
        }
        source = source.substring(0, newline + 1)
                + "!pragma layout smetana\nskinparam backgroundColor "
                + (appearance == Appearance.DARK
                ? "#202020\nskinparam defaultFontColor #eeeeee\n"
                : "#ffffff\nskinparam defaultFontColor #202020\n")
                + source.substring(newline + 1);
        try (var out = new LimitedOutputStream(Files.newOutputStream(output), 10_000_000)) {
            var reader = new SourceStringReader(source);
            if (!reader.getBlocks().isEmpty()
                    && reader.getBlocks().getFirst().getDiagram() instanceof AbstractDiagram diagram) {
                // Compose density with the document's scale; FileFormatOption does not scale UML PNGs.
                var documentScale = diagram.getScale();
                diagram.setScale((width, height) -> scale * (documentScale == null
                        ? 1
                        : documentScale.getScale(width, height)));
            }
            var result = reader.outputImage(out, new FileFormatOption(FileFormat.PNG));
            if (result == null || result.getDescription().contains("Error")) {
                throw new IOException("Invalid or unsupported PlantUML diagram");
            }
        }
    }
}

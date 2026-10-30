// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.document;

import java.nio.file.Path;
import java.util.List;

public record Document(List<Block> blocks, Path baseDirectory) {
    public Document {
        blocks = List.copyOf(blocks);
        baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }
}

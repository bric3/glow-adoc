// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.document;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public record Document(List<Block> blocks, Path baseDirectory, List<Path> imageRoots) {
    public Document(List<Block> blocks, Path baseDirectory) {
        this(blocks, baseDirectory, List.of());
    }

    public Document {
        blocks = List.copyOf(blocks);
        baseDirectory = baseDirectory.toAbsolutePath().normalize();
        imageRoots =
                Stream.concat(Stream.of(baseDirectory), imageRoots.stream())
                        .map(path -> path.toAbsolutePath().normalize())
                        .distinct()
                        .toList();
    }
}

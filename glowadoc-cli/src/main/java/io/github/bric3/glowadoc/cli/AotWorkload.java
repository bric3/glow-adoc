// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Owns the temporary files needed for the bundled corpus's includes and images.
 */
final class AotWorkload implements AutoCloseable {
    private static final String RESOURCES = "/META-INF/glow-adoc/aot/";
    private final Path directory;

    private AotWorkload(Path directory) {
        this.directory = directory;
    }

    static AotWorkload open() throws IOException {
        var workload = new AotWorkload(Files.createTempDirectory("glow-adoc-aot-"));
        try {
            String manifest;
            try (var input = resource("corpus.list")) {
                manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : manifest.lines().toList()) {
                String name = line.strip();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                Path target = workload.directory.resolve(name).normalize();
                if (!target.startsWith(workload.directory)) {
                    throw new IOException("Invalid AOT corpus path: " + name);
                }
                Files.createDirectories(target.getParent());
                try (var input = resource(name)) {
                    Files.copy(input, target);
                }
            }
            return workload;
        } catch (IOException | RuntimeException failure) {
            try {
                workload.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    Path document() {
        return directory.resolve("workload.adoc");
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = AotWorkload.class.getResourceAsStream(RESOURCES + name);
        if (input == null) {
            throw new IOException("Bundled AOT corpus resource is unavailable: " + name);
        }
        return input;
    }

    @Override
    public void close() throws IOException {
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}

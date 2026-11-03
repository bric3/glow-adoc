// SPDX-License-Identifier: MPL-2.0
//JAVA 27
//SOURCES Benchmark.java
package io.github.bric3.glowadoc.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compare runtime settings without changing application defaults.
 */
public class StartupMatrix {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: StartupMatrix.java JAVA JAR FILE");
        }
        Path temporary = Files.createTempDirectory("glow-startup-matrix-");
        Path small = Files.writeString(temporary.resolve("small.adoc"), "= Small\n\nA short paragraph.\n");
        Path large = Files.writeString(
                temporary.resolve("large.adoc"),
                Files.readString(Path.of("fixtures/showcase.adoc")).repeat(10)
        );
        var variants = List.of(
                List.<String>of(),
                List.of("-Djruby.compile.mode=OFF"),
                List.of("-XX:TieredStopAtLevel=1"),
                List.of("-Djruby.compile.mode=OFF", "-XX:TieredStopAtLevel=1"),
                List.of("-Djruby.compile.invokedynamic=false", "-Djruby.invokedynamic.enabled=false"),
                List.of("-XX:+UseSerialGC", "-Xms32m", "-Xmx512m")
        );
        try {
            for (var flags : variants) {
                for (var file : List.of(small.toString(), args[2], large.toString())) {
                    System.out.println("# flags=" + flags + " file=" + Path.of(file).getFileName());
                    var command = new ArrayList<>(List.of("--runs", "3", "--", args[0]));
                    command.addAll(flags);
                    command.addAll(List.of("-jar", args[1], "--no-pager", "--color=always", file));
                    try {
                        Benchmark.main(command.toArray(String[]::new));
                    } catch (Exception e) {
                        System.out.println("# FAILED: " + e.getMessage().lines().findFirst().orElse(e.toString()));
                    }
                }
            }
        } finally {
            Files.deleteIfExists(small);
            Files.deleteIfExists(large);
            Files.deleteIfExists(temporary);
        }
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

class AotWorkloadTest {
    @Test
    void extractsTheSelfContainedCorpusAndRemovesItsTemporaryFiles() throws Exception {
        Path directory;
        try (var workload = AotWorkload.open()) {
            directory = workload.document().getParent();
            assertTrue(Files.readString(workload.document()).contains("glow-adoc AOT corpus"));
            assertTrue(Files.readString(directory.resolve("code.adoc")).contains("CompletableFuture"));
            assertTrue(Files.readString(directory.resolve("resources/worker.svg")).contains("<svg"));
        }
        assertFalse(Files.exists(directory));
    }
}

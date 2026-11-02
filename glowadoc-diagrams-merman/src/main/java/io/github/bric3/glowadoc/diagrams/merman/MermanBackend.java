// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams.merman;

import io.github.bric3.glowadoc.diagrams.DiagramBackend;
import io.github.bric3.glowadoc.diagrams.LimitedOutputStream;
import io.github.bric3.glowadoc.document.Appearance;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MermanBackend implements DiagramBackend {
    private final String command;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Process> processes = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public MermanBackend(String command) {
        this.command = command;
    }

    @Override
    public void render(String source, Path output, Appearance appearance) throws Exception {
        Path directory = output.getParent();
        Path input = Files.createTempFile(directory, "source-", ".mmd");
        try {
            Files.writeString(input, source);
            var process = new ProcessBuilder(
                    command,
                    "render",
                    input.toString(),
                    "--format",
                    "png",
                    "--theme",
                    appearance == Appearance.DARK ? "dark" : "default",
                    "--resource-profile",
                    "constrained",
                    "--operation-timeout-ms",
                    "14000",
                    "--output",
                    "-"
            )
                .directory(directory.toFile())
                .start();
            processes.add(process);
            try {
                if (closed) {
                    throw new InterruptedException("Diagram renderer closed");
                }
                var stdout = workers.submit(() -> {
                    try (var in = process.getInputStream();
                            var out = new LimitedOutputStream(Files.newOutputStream(output), 10_000_000)
                    ) {
                        in.transferTo(out);
                    } catch (IOException e) {
                        stop(process);
                        throw new UncheckedIOException(e);
                    }
                });
                var stderr = workers.submit(() -> {
                    try (var in = process.getErrorStream();
                            var out = new LimitedOutputStream(OutputStream.nullOutputStream(), 64_000)
                    ) {
                        in.transferTo(out);
                    } catch (IOException e) {
                        stop(process);
                        throw new UncheckedIOException(e);
                    }
                });
                try {
                    if (!process.waitFor(15, TimeUnit.SECONDS)) {
                        throw new IOException("Merman timed out");
                    }
                    stdout.get(1, TimeUnit.SECONDS);
                    stderr.get(1, TimeUnit.SECONDS);
                    if (process.exitValue() != 0) {
                        throw new IOException("Merman exited " + process.exitValue());
                    }
                } finally {
                    stdout.cancel(true);
                    stderr.cancel(true);
                }
            } finally {
                stop(process);
                processes.remove(process);
            }
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    @Override
    public void close() {
        closed = true;
        processes.forEach(MermanBackend::stop);
        workers.shutdownNow();
        try {
            workers.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

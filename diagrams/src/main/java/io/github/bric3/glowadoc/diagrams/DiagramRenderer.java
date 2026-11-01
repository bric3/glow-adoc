// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.diagrams;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Document;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DiagramRenderer implements AutoCloseable {
    private final String merman;
    private final Path directory;

    private record Key(String language, String source, Appearance appearance) {}

    private final Map<Key, Future<Optional<Path>>> cache = new ConcurrentHashMap<>();
    private final ExecutorService renders =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final Queue<String> diagnostics = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile boolean closed;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Process> processes = ConcurrentHashMap.newKeySet();
    private final java.util.function.Consumer<String> diagnostic;

    public DiagramRenderer(String merman, java.util.function.Consumer<String> diagnostic)
            throws IOException {
        this.merman = merman;
        this.diagnostic = diagnostic;
        directory = Files.createTempDirectory("glow-adoc-");
        System.setProperty("PLANTUML_SECURITY_PROFILE", "SANDBOX");
        System.setProperty("java.awt.headless", "true");
    }

    public Optional<Path> render(Block block, Appearance appearance) {
        var task = task(block, appearance, false);
        try {
            return task.get();
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (CancellationException e) {
            return Optional.empty();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Diagram rendering failed", e.getCause());
        }
    }

    /** Enqueue a render without delaying the terminal frame. */
    public Optional<Path> request(Block block, Appearance appearance) {
        if (!closed) {
            task(block, appearance, true);
        }
        return cached(block, appearance);
    }

    /** Keep the previous theme's image visible while its replacement is rendering. */
    public Optional<Path> cached(Block block, Appearance appearance) {
        if (closed) {
            return Optional.empty();
        }
        Key key = new Key(block.language(), block.text(), appearance);
        var future = cache.get(key);
        if (future == null || !future.isDone()) {
            future =
                    cache.get(
                            new Key(
                                    key.language(),
                                    key.source(),
                                    appearance == Appearance.DARK
                                            ? Appearance.LIGHT
                                            : Appearance.DARK));
        }
        if (future == null || !future.isDone()) {
            return Optional.empty();
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (CancellationException | ExecutionException e) {
            return Optional.empty();
        }
    }

    public boolean takeDirty() {
        return dirty.getAndSet(false);
    }

    public List<String> takeDiagnostics() {
        var result = new ArrayList<String>();
        String message;
        while ((message = diagnostics.poll()) != null) {
            result.add(message);
        }
        return result;
    }

    private Future<Optional<Path>> task(Block block, Appearance appearance, boolean background) {
        if (closed) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Key key = new Key(block.language(), block.text(), appearance);
        return cache.computeIfAbsent(
                key,
                ignored -> {
                    var task =
                            new FutureTask<Optional<Path>>(() -> renderNow(key, background)) {
                                @Override
                                protected void done() {
                                    if (background && !closed) {
                                        dirty.set(true);
                                    }
                                }
                            };
                    try {
                        renders.execute(task);
                    } catch (RejectedExecutionException e) {
                        task.cancel(false);
                    }
                    return task;
                });
    }

    private Optional<Path> renderNow(Key key, boolean background) {
        if (closed) {
            return Optional.empty();
        }
        try {
            if (key.source().length() > 100_000) {
                throw new IOException("Diagram source exceeds 100 KB");
            }
            Path output = Files.createTempFile(directory, "diagram-", ".png");
            if (key.language().equals("plantuml")) {
                var task =
                        workers.submit(
                                () -> {
                                    plantuml(key.source(), output, key.appearance());
                                    return output;
                                });
                try {
                    task.get(15, TimeUnit.SECONDS);
                } finally {
                    task.cancel(true);
                }
            } else if (key.language().equals("mermaid")) {
                mermaid(key.source(), output, key.appearance());
            } else {
                throw new IOException("Unknown diagram language: " + key.language());
            }
            if (Files.size(output) == 0) {
                throw new IOException("Diagram renderer produced no image");
            }
            return Optional.of(output);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String message =
                    key.language()
                            + ": "
                            + Controls.visible(String.valueOf(e.getMessage()))
                            + "; showing source";
            if (!closed) {
                if (background) {
                    diagnostics.add(message);
                } else {
                    diagnostic.accept(message);
                }
            }
            return Optional.empty();
        }
    }

    private static void plantuml(String source, Path output, Appearance appearance)
            throws IOException {
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
        source =
                source.substring(0, newline + 1)
                        + "!pragma layout smetana\nskinparam backgroundColor "
                        + (appearance == Appearance.DARK
                                ? "#202020\nskinparam defaultFontColor #eeeeee\n"
                                : "#ffffff\nskinparam defaultFontColor #202020\n")
                        + source.substring(newline + 1);
        try (var out = new LimitedOutput(Files.newOutputStream(output), 10_000_000)) {
            var result =
                    new SourceStringReader(source)
                            .outputImage(out, new FileFormatOption(FileFormat.PNG));
            if (result == null || result.getDescription().contains("Error")) {
                throw new IOException("Invalid or unsupported PlantUML diagram");
            }
        }
    }

    private void mermaid(String source, Path output, Appearance appearance) throws Exception {
        Path input = Files.createTempFile(directory, "source-", ".mmd");
        Files.writeString(input, source);
        var process =
                new ProcessBuilder(
                                merman,
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
                                "-")
                        .directory(directory.toFile())
                        .start();
        processes.add(process);
        if (closed) {
            process.destroyForcibly();
            processes.remove(process);
            throw new InterruptedException("Diagram renderer closed");
        }
        var stdout =
                workers.submit(
                        () -> {
                            try (var in = process.getInputStream();
                                    var out =
                                            new LimitedOutput(
                                                    Files.newOutputStream(output), 10_000_000)) {
                                in.transferTo(out);
                            } catch (IOException e) {
                                process.destroyForcibly();
                                throw new UncheckedIOException(e);
                            }
                        });
        var stderr =
                workers.submit(
                        () -> {
                            try (var in = process.getErrorStream();
                                    var out =
                                            new LimitedOutput(
                                                    OutputStream.nullOutputStream(), 64_000)) {
                                in.transferTo(out);
                            } catch (IOException e) {
                                process.destroyForcibly();
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
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            stdout.cancel(true);
            stderr.cancel(true);
            processes.remove(process);
            Files.deleteIfExists(input);
        }
    }

    public static Optional<Path> localImage(Document document, String target) {
        for (Path root : document.imageRoots()) {
            try {
                Path directory = root.toRealPath();
                Path path = directory.resolve(target).normalize().toRealPath();
                if (path.startsWith(directory) && Files.isRegularFile(path)) {
                    return Optional.of(path);
                }
            } catch (IOException | InvalidPathException e) {
                // Try the next allowed image directory.
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        renders.shutdownNow();
        cache.values().forEach(future -> future.cancel(true));
        processes.forEach(
                p -> {
                    p.descendants().forEach(ProcessHandle::destroyForcibly);
                    p.destroyForcibly();
                });
        workers.shutdownNow();
        try {
            renders.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class LimitedOutput extends FilterOutputStream {
        private final long maximum;
        private long written;

        LimitedOutput(OutputStream output, long maximum) {
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
}

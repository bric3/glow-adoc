// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.diagrams;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Document;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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
import java.util.function.Consumer;

public final class DiagramRenderer implements AutoCloseable {
    private final Map<String, DiagramBackend> backends;
    private final Path directory;

    private record Key(String language, String source, Appearance appearance, double scale) {
        Key {
            if (!Double.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Diagram scale must be finite and positive");
            }
        }
    }

    private final Map<Key, Future<Optional<Path>>> cache = new ConcurrentHashMap<>();
    private final ExecutorService renders = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final Queue<String> diagnostics = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile boolean closed;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Consumer<String> diagnostic;

    public DiagramRenderer(Map<String, DiagramBackend> backends, Consumer<String> diagnostic) throws IOException {
        this.backends = Map.copyOf(backends);
        this.diagnostic = diagnostic;
        directory = Files.createTempDirectory("glow-adoc-");
    }

    public Optional<Path> render(Block block, Appearance appearance, double scale) {
        var task = task(block, appearance, scale, false);
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

    /**
     * Enqueue a render without delaying the terminal frame.
     */
    public Optional<Path> request(Block block, Appearance appearance, double scale) {
        if (!closed) {
            task(block, appearance, scale, true);
        }
        return cached(block, appearance, scale);
    }

    /**
     * Keep the previous theme's image visible while its replacement is rendering.
     */
    public Optional<Path> cached(Block block, Appearance appearance, double scale) {
        if (closed) {
            return Optional.empty();
        }
        Key key = new Key(block.language(), block.text(), appearance, scale);
        var future = cache.get(key);
        if (future == null || !future.isDone()) {
            future = cache.get(
                    new Key(
                            key.language(),
                            key.source(),
                            appearance == Appearance.DARK ? Appearance.LIGHT : Appearance.DARK,
                            scale
                    )
            );
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

    private Future<Optional<Path>> task(Block block, Appearance appearance, double scale, boolean background) {
        if (closed) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Key key = new Key(block.language(), block.text(), appearance, scale);
        return cache.computeIfAbsent(key, ignored -> {
            var task = new FutureTask<Optional<Path>>(() -> renderNow(key, background)) {
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
            DiagramBackend backend = backends.get(key.language());
            if (backend == null) {
                throw new IOException("Unknown diagram language: " + key.language());
            }
            var task = workers.submit(() -> {
                backend.render(key.source(), output, key.appearance(), key.scale());
                return output;
            });
            try {
                task.get(15, TimeUnit.SECONDS);
            } finally {
                task.cancel(true);
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
                    key.language() + ": " + Controls.visible(String.valueOf(e.getMessage())) + "; showing source";
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
        backends.values().forEach(DiagramBackend::close);
        workers.shutdownNow();
        try {
            renders.awaitTermination(1, TimeUnit.SECONDS);
            // Let backend render calls release temporary files before deleting the directory.
            workers.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

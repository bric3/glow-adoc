// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.terminal;

import io.github.bric3.glowadoc.document.Controls;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Opens explicitly clicked links without blocking the terminal event loop. */
public final class LinkOpener implements AutoCloseable {
    private final List<String> command;
    private final boolean remote;
    private final Queue<String> diagnostics = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().daemon().name("glow-adoc-browser").factory());
    private Future<?> pending;
    private volatile Process active;
    private volatile boolean closed;

    public LinkOpener() {
        this(command(System.getProperty("os.name")), remote(System.getenv()));
    }

    LinkOpener(List<String> command, boolean remote) {
        this.command = List.copyOf(command);
        this.remote = remote;
    }

    static List<String> command(String os) {
        return os.startsWith("Mac") ? List.of("/usr/bin/open", "--") : List.of("xdg-open");
    }

    static boolean remote(Map<String, String> env) {
        return List.of("SSH_CONNECTION", "SSH_CLIENT", "SSH_TTY").stream()
                .anyMatch(name -> !env.getOrDefault(name, "").isBlank());
    }

    public void open(String target) {
        if (closed || pending != null && !pending.isDone()) {
            return;
        }
        try {
            if (!Controls.safeLink(target)) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(target);
            if (!uri.getScheme().equals("mailto")
                    && (uri.getHost() == null || uri.getHost().isBlank())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            diagnostics.add("Cannot open this link");
            return;
        }
        if (remote) {
            diagnostics.add("SSH: use terminal link gesture, or m to release mouse");
            return;
        }
        pending =
                worker.submit(
                        () -> {
                            Process process = null;
                            try {
                                var arguments = new ArrayList<>(command);
                                arguments.add(target);
                                process =
                                        new ProcessBuilder(arguments)
                                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                                .start();
                                active = process;
                                process.getOutputStream().close();
                                if (closed) {
                                    return;
                                }
                                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                                    diagnostics.add("Browser opener timed out");
                                } else if (process.exitValue() != 0) {
                                    diagnostics.add(
                                            "Browser opener failed; use terminal modifier-click");
                                } else {
                                    diagnostics.add("Opened link");
                                }
                            } catch (IOException e) {
                                diagnostics.add(
                                        "Browser opener unavailable; use terminal modifier-click");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                if (process != null && process.isAlive()) {
                                    // The launched browser belongs to the user; cancel only its
                                    // opener.
                                    process.destroyForcibly();
                                }
                                active = null;
                            }
                        });
    }

    public List<String> takeDiagnostics() {
        var result = new ArrayList<String>();
        String diagnostic;
        while ((diagnostic = diagnostics.poll()) != null) {
            result.add(diagnostic);
        }
        return result;
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        Process process = active;
        if (process != null) {
            process.destroyForcibly();
        }
    }
}

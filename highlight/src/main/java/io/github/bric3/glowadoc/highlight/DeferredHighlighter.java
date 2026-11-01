// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.highlight;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Span;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows source immediately and highlights it after the first frame. Owns its delegate. */
public final class DeferredHighlighter implements Highlighter {
    private record Key(String source, String language, Appearance appearance) {}

    private final Highlighter delegate;
    private final Map<Key, List<Span>> cache = new HashMap<>();
    private final Set<Key> requested = new LinkedHashSet<>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private boolean started, active, closed, dirty;

    public DeferredHighlighter(Highlighter delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized List<Span> highlight(
            String source, String language, Appearance appearance) {
        var key = new Key(source, language, appearance);
        var result = cache.get(key);
        if (result != null) {
            return result;
        }
        if (!closed && !language.isBlank() && requested.add(key) && started) {
            schedule(key);
        }
        return List.of(new Span(source));
    }

    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        requested.forEach(this::schedule);
    }

    public synchronized boolean takeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    public synchronized boolean isIdle() {
        return started && cache.size() == requested.size();
    }

    private void schedule(Key key) {
        worker.execute(
                () -> {
                    synchronized (this) {
                        if (closed) {
                            return;
                        }
                        active = true;
                    }
                    List<Span> result = null;
                    try {
                        result =
                                List.copyOf(
                                        delegate.highlight(
                                                key.source(), key.language(), key.appearance()));
                    } catch (RuntimeException e) {
                        result = List.of(new Span(key.source()));
                    } finally {
                        synchronized (this) {
                            active = false;
                            if (closed) {
                                delegate.close();
                            } else if (result != null) {
                                cache.put(key, result);
                                dirty = true;
                            }
                        }
                    }
                });
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        worker.shutdownNow();
        if (!active) {
            delegate.close();
        }
    }
}

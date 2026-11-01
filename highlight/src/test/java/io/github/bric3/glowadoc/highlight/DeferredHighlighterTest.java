// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.highlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class DeferredHighlighterTest {
    @Test
    void closeCancelsQueuedWorkAndClosesTheDelegateAfterActiveWork() throws Exception {
        var entered = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var deferred =
                new DeferredHighlighter(
                        new Highlighter() {
                            @Override
                            public List<Span> highlight(
                                    String source, String language, Appearance appearance) {
                                calls.incrementAndGet();
                                entered.countDown();
                                try {
                                    new CountDownLatch(1).await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return List.of(new Span(source));
                            }

                            @Override
                            public void close() {
                                closed.countDown();
                            }
                        });
        deferred.highlight("one", "java", Appearance.DARK);
        deferred.highlight("two", "java", Appearance.DARK);
        deferred.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        deferred.close();
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    void firstFrameDoesNotWaitForHighlightingAndReflowReusesResults() throws Exception {
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var source = "int n = 1;";
        var styled = List.of(new Span(source, Style.PLAIN.bolded()));
        try (var highlighter =
                new DeferredHighlighter(
                        (text, lang, mode) -> {
                            calls.incrementAndGet();
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            finished.countDown();
                            return styled;
                        })) {
            assertEquals(
                    List.of(new Span(source)),
                    highlighter.highlight(source, "java", Appearance.DARK));
            assertEquals(0, calls.get());
            highlighter.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(
                    List.of(new Span(source)),
                    highlighter.highlight(source, "java", Appearance.DARK));
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100 && !highlighter.takeDirty(); i++) {
                Thread.sleep(10);
            }
            assertEquals(styled, highlighter.highlight(source, "java", Appearance.DARK));
            assertTrue(highlighter.isIdle());
            assertEquals(styled, highlighter.highlight(source, "java", Appearance.DARK));
            assertEquals(1, calls.get());
        }
    }
}

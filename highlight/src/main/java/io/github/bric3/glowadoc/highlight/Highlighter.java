// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.highlight;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Span;

import java.util.List;

public interface Highlighter extends AutoCloseable {
    List<Span> highlight(String source, String language, Appearance appearance);

    @Override
    default void close() {}
}

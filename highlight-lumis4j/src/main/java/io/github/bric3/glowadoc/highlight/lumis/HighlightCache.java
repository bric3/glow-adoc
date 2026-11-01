// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.highlight.lumis;

import io.github.bric3.glowadoc.cache.CacheFiles;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.roastedroot.lumis4j.core.Lumis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/** Persisted terminal spans, validated against the source and bundled highlighter. */
final class HighlightCache {
    private final CacheFiles files;
    private final String identity;

    HighlightCache(Path directory, String identity) {
        files = new CacheFiles(directory);
        this.identity = identity;
    }

    static HighlightCache open(Path directory) {
        if (directory == null) {
            return null;
        }
        String identity = CacheFiles.artifactIdentity(HighlightCache.class, Lumis.class);
        return identity == null ? null : new HighlightCache(directory, identity);
    }

    List<Span> read(String source, String language, Appearance appearance) {
        String slot = slot(source, language, appearance);
        byte[] bytes = files.read(slot, identity);
        if (bytes == null) {
            return null;
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int count = in.readInt();
            if (count < 0 || count > 100_000) {
                return null;
            }
            var spans = new ArrayList<Span>();
            for (int i = 0; i < count; i++) {
                int length = in.readInt();
                if (length < 0 || length > in.available()) {
                    return null;
                }
                String text = new String(in.readNBytes(length), StandardCharsets.UTF_8);
                int flags = in.readUnsignedByte();
                if ((flags & ~31) != 0) {
                    return null;
                }
                var style =
                        new Style(
                                (flags & 1) != 0,
                                (flags & 2) != 0,
                                (flags & 4) != 0,
                                (flags & 8) != 0,
                                in.readUTF(),
                                in.readUTF(),
                                in.readUTF(),
                                (flags & 16) != 0);
                if (!colour(style.foreground())
                        || !colour(style.background())
                        || (!style.link().isEmpty() && !Controls.safeLink(style.link()))) {
                    return null;
                }
                spans.add(new Span(text, style));
            }
            return in.available() == 0 && text(spans).equals(source) ? List.copyOf(spans) : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    void write(String source, String language, Appearance appearance, List<Span> spans) {
        if (source.length() > 1_000_000 || spans.size() > 100_000 || !text(spans).equals(source)) {
            return;
        }
        try (var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes)) {
            out.writeInt(spans.size());
            for (Span span : spans) {
                byte[] text = span.text().getBytes(StandardCharsets.UTF_8);
                out.writeInt(text.length);
                out.write(text);
                Style style = span.style();
                out.writeByte(
                        (style.bold() ? 1 : 0)
                                | (style.italic() ? 2 : 0)
                                | (style.code() ? 4 : 0)
                                | (style.underline() ? 8 : 0)
                                | (style.strike() ? 16 : 0));
                out.writeUTF(style.foreground());
                out.writeUTF(style.background());
                out.writeUTF(style.link());
            }
            files.write(slot(source, language, appearance), identity, bytes.toByteArray());
        } catch (IOException ignored) {
            // Unsupported entries leave the freshly highlighted spans available.
        }
    }

    private static String slot(String source, String language, Appearance appearance) {
        return CacheFiles.key(List.of(source, language, appearance.name()));
    }

    private static String text(List<Span> spans) {
        return spans.stream().map(Span::text).collect(Collectors.joining());
    }

    private static boolean colour(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (value.length() != 7 || value.charAt(0) != '#') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!HexFormat.isHexDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}

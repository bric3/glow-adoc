// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.highlight.lumis;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.highlight.Highlighter;
import io.roastedroot.lumis4j.core.Lang;
import io.roastedroot.lumis4j.core.Lumis;
import io.roastedroot.lumis4j.core.Theme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class LumisHighlighter implements Highlighter {
    private Lumis lumis;
    private final Path cacheDirectory;
    private HighlightCache cache;
    private boolean cacheReady;

    public LumisHighlighter() {
        this(null);
    }

    public LumisHighlighter(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    public List<Span> highlight(String source, String language, Appearance appearance) {
        source = Controls.visible(source);
        if (language.isBlank()) {
            return List.of(new Span(source));
        }
        try {
            var lang = Lang.fromString(language);
            if (!cacheReady) {
                cache = HighlightCache.open(cacheDirectory);
                cacheReady = true;
            }
            if (cache != null) {
                var cached = cache.read(source, language, appearance);
                if (cached != null) {
                    return cached;
                }
            }
            if (lumis == null) {
                lumis = Lumis.builder().build();
            }
            String ansi = lumis
                .highlighter()
                .withLang(lang)
                .withTheme(appearance == Appearance.LIGHT ? Theme.GITHUB_LIGHT : Theme.GITHUB_DARK)
                .build()
                .highlight(source)
                .string();
            var spans = decode(ansi);
            // A formatter must preserve the exact source, including its final newline.
            String rendered = spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining());
            if (rendered.equals(source)) {
                if (cache != null) {
                    cache.write(source, language, appearance, spans);
                }
                return spans;
            }
            if (rendered.equals(source + "\n")) {
                var last = spans.removeLast();
                spans.add(new Span(last.text().substring(0, last.text().length() - 1), last.style()));
                if (cache != null) {
                    cache.write(source, language, appearance, spans);
                }
                return spans;
            }
        } catch (RuntimeException e) {
            // Unknown grammars and formatter failures leave the source readable.
        }
        return List.of(new Span(source));
    }

    static List<Span> decode(String ansi) {
        var spans = new ArrayList<Span>();
        var matcher = Pattern.compile("\\[([0-9;]*)m").matcher(ansi);
        Style style = Style.PLAIN;
        int start = 0;
        while (matcher.find()) {
            if (matcher.start() > start) {
                spans.add(new Span(Controls.visible(ansi.substring(start, matcher.start())), style));
            }
            String[] codes = matcher.group(1).isEmpty() ? new String[] {"0"} : matcher.group(1).split(";");
            for (int i = 0; i < codes.length; i++) {
                int code = Integer.parseInt(codes[i]);
                if (code == 0) {
                    style = Style.PLAIN;
                } else if (code == 1) {
                    style = style.bolded();
                } else if (code == 3) {
                    style = style.italicised();
                } else if ((code == 38 || code == 48) && i + 4 < codes.length && codes[i + 1].equals("2")) {
                    String colour =
                            String.format(
                                    "#%02x%02x%02x",
                                    channel(codes[i + 2]),
                                    channel(codes[i + 3]),
                                    channel(codes[i + 4])
                    );
                    style = new Style(
                            style.bold(),
                            style.italic(),
                            false,
                            false,
                            code == 38 ? colour : style.foreground(),
                            code == 48 ? colour : style.background(),
                            ""
                    );
                    i += 4;
                }
            }
            start = matcher.end();
        }
        if (start < ansi.length()) {
            spans.add(new Span(Controls.visible(ansi.substring(start)), style));
        }
        return spans;
    }

    private static int channel(String value) {
        return Math.clamp(Integer.parseInt(value), 0, 255);
    }

    @Override
    public void close() {
        if (lumis != null) {
            lumis.close();
        }
    }
}

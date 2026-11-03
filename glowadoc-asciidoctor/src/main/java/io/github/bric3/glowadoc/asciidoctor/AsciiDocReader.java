// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.asciidoctor;

import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.xml.parsers.DocumentBuilderFactory;

public final class AsciiDocReader implements AutoCloseable {
    private Asciidoctor engine;
    private final ConversionCache cache;
    private final Consumer<String> milestone;
    private Path includeRoot;
    private boolean showShortcodes;
    private boolean includesUsed;

    public AsciiDocReader() {
        this(null, ignored -> {});
    }

    public AsciiDocReader(Path input, Path cacheDirectory, Consumer<String> milestone) {
        this(ConversionCache.open(input, cacheDirectory), milestone);
    }

    AsciiDocReader(ConversionCache cache, Consumer<String> milestone) {
        this.cache = cache;
        this.milestone = milestone;
    }

    private Asciidoctor engine() {
        if (engine != null) {
            return engine;
        }
        engine = Asciidoctor.Factory.create();
        engine.javaConverterRegistry().register(TerminalConverter.class);
        engine.javaExtensionRegistry().includeProcessor(new IncludeProcessor() {
            @Override
            public boolean handles(String target) {
                return true;
            }

            @Override
            public void process(
                    org.asciidoctor.ast.Document document,
                    PreprocessorReader reader,
                    String target,
                    Map<String, Object> attributes
            ) {
                includesUsed = true;
                try {
                    Path path = Path.of(reader.getDir()).resolve(target).normalize().toRealPath();
                    if (!path.startsWith(includeRoot)) {
                        throw new IOException("Include is outside the document directory");
                    }
                    if (Files.size(path) > 4_000_000) {
                        throw new IOException("Include exceeds 4 MB");
                    }
                    if (attributes.containsKey("tag")
                            || attributes.containsKey("tags")
                            || attributes.containsKey("lines")) {
                        throw new IOException("Include selectors are not supported yet");
                    }
                    reader.pushInclude(
                            showShortcodes ? Files.readString(path) : Shortcodes.hide(Files.readString(path)),
                            path.toString(),
                            path.toString(),
                            1,
                            attributes
                    );
                } catch (IOException | java.nio.file.InvalidPathException e) {
                    reader.pushInclude(
                            "[include unavailable: " + Controls.visible(target) + "]",
                            target,
                            target,
                            1,
                            Map.of()
                    );
                }
            }
        });
        milestone.accept("jruby-ready");
        return engine;
    }

    public Document read(String source, Path baseDirectory) {
        return read(source, baseDirectory, false);
    }

    public Document read(String source, Path baseDirectory, boolean showShortcodes) {
        return read(source, baseDirectory, showShortcodes, Map.of());
    }

    public Document read(String source, Path baseDirectory, boolean showShortcodes, Map<String, String> attributes) {
        return read(source, baseDirectory, showShortcodes, true, attributes);
    }

    public Document read(
            String source,
            Path baseDirectory,
            boolean showShortcodes,
            boolean frontMatterTitle,
            Map<String, String> attributes
    ) {
        try {
            this.showShortcodes = showShortcodes;
            includeRoot = baseDirectory.toRealPath();
            int frontMatterEnd = frontMatterEnd(source);
            String frontMatter = Controls.visible(source.substring(source.startsWith("﻿") ? 1 : 0, frontMatterEnd));
            String key =
                    cache == null ? null : cache.key(source, includeRoot, showShortcodes, frontMatterTitle, attributes);
            String cached = cache == null ? null : cache.read(key);
            if (cached != null) {
                try {
                    Document document = parse(cached, baseDirectory, frontMatter);
                    milestone.accept("conversion-cache-hit");
                    return document;
                } catch (Exception ignored) {
                    // Corrupt entries are replaced after a successful conversion.
                }
            }
            includesUsed = false;
            var conversionAttributes = Attributes.builder();
            attributes.forEach((name, value) -> conversionAttributes.attribute(name, Controls.visible(value)));
            conversionAttributes
                .attribute("allow-uri-read", false)
                // The terminal highlighter owns source styling.
                .attribute("source-highlighter", "none");
            var converted = engine()
                .load(
                        Controls.visible(
                                showShortcodes ? source.substring(frontMatterEnd) : Shortcodes.hide(source.substring(
                                        frontMatterEnd
                                ))
                        ),
                        Options
                            .builder()
                            .backend("glow-terminal")
                            .safe(SafeMode.SAFE)
                            .baseDir(baseDirectory.toFile())
                            .toFile(false)
                            .attributes(conversionAttributes.build())
                            .build()
                );
            if (frontMatterTitle && !converted.hasAttribute("doctitle")) {
                String title = frontMatterTitle(frontMatter);
                if (!title.isEmpty()) {
                    converted.setTitle(title);
                    converted.setAttribute("doctitle", title, true);
                }
            }
            String xml = HtmlContent.normalise(converted.convert());
            Document document = parse(xml, baseDirectory, frontMatter);
            // Includes can change independently of the top-level source and attributes.
            if (cache != null && !includesUsed) {
                cache.write(key, xml);
            }
            return document;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read AsciiDoc: " + e.getMessage(), e);
        }
    }

    private static String frontMatterTitle(String frontMatter) {
        if (frontMatter.isEmpty() || frontMatter.length() > 256 * 1024) {
            return "";
        }
        int start = frontMatter.indexOf('\n') + 1;
        int end = frontMatter.lastIndexOf('\n');
        if (start >= end) {
            return "";
        }
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(256 * 1024);
        try {
            String yaml = frontMatter.substring(start, end);
            Object metadata = new Yaml(new SafeConstructor(options)).load(yaml);
            if (metadata instanceof Map<?, ?> values && values.get("title") instanceof String title) {
                return Controls.visible(title).strip();
            }
        } catch (YAMLException ignored) {
            // Invalid metadata retains its literal display and does not prevent reading the body.
        }
        return "";
    }

    private static int frontMatterEnd(String source) {
        int start = source.startsWith("﻿") ? 1 : 0;
        int end = source.indexOf('\n', start);
        if (end < 0 || !source.substring(start, end).stripTrailing().equals("---")) {
            return start;
        }
        start = end + 1;
        while (start < source.length()) {
            end = source.indexOf('\n', start);
            if (end < 0) {
                end = source.length();
            }
            String line = source.substring(start, end).stripTrailing();
            if (line.equals("---") || line.equals("...")) {
                return end > start && source.charAt(end - 1) == '\r' ? end - 1 : end;
            }
            start = end + 1;
        }
        return source.startsWith("﻿") ? 1 : 0;
    }

    private static Document parse(String xml, Path baseDirectory, String frontMatter) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        var root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        var blocks = blocks(root);
        if (!frontMatter.isEmpty()) {
            blocks.addFirst(
                    new Block(
                            Block.Kind.CODE,
                            "",
                            List.of(new Span(frontMatter)),
                            List.of(),
                            "yaml",
                            "",
                            new Block.Format("", "front-matter", "left", 1, 1, 0, false)
                    )
            );
        }
        return new Document(blocks, baseDirectory);
    }

    private static List<Block> blocks(Element parent) {
        var blocks = new ArrayList<Block>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && !inline(element) && !element.getTagName().equals("title")) {
                blocks.add(block(element));
            }
        }
        return blocks;
    }

    private static Block block(Element element) {
        Block.Kind kind = switch (element.getTagName()) {
            case "heading" -> Block.Kind.HEADING;
            case "source" -> Block.Kind.CODE;
            case "list" -> Block.Kind.LIST;
            case "item" -> Block.Kind.ITEM;
            case "table" -> Block.Kind.TABLE;
            case "row" -> Block.Kind.ROW;
            case "cell" -> Block.Kind.CELL;
            case "quote" -> Block.Kind.QUOTE;
            case "verse" -> Block.Kind.VERSE;
            case "example" -> Block.Kind.EXAMPLE;
            case "sidebar" -> Block.Kind.SIDEBAR;
            case "open" -> Block.Kind.OPEN;
            case "details" -> Block.Kind.DETAILS;
            case "admonition" -> Block.Kind.ADMONITION;
            case "image" -> Block.Kind.IMAGE;
            case "diagram" -> Block.Kind.DIAGRAM;
            default -> Block.Kind.PARAGRAPH;
        };
        var spans = new ArrayList<Span>();
        inlines(element, Style.PLAIN, spans);
        var titleSpans = new ArrayList<Span>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element title && title.getTagName().equals("title")) {
                inlines(title, Style.PLAIN, titleSpans);
            }
        }
        if (titleSpans.isEmpty() && !element.getAttribute("title").isEmpty()) {
            titleSpans.add(new Span(Controls.visible(element.getAttribute("title"))));
        }
        if (kind != Block.Kind.CODE
                && kind != Block.Kind.DIAGRAM
                && kind != Block.Kind.VERSE
                && spans.stream().allMatch(span -> span.text().isBlank())) {
            spans.clear();
        }
        return new Block(
                kind,
                element.getAttribute("id"),
                spans,
                blocks(element),
                element.getAttribute("language"),
                element.getAttribute("target"),
                new Block.Format(
                        titleSpans,
                        element.getAttribute("variant"),
                        element.getAttribute("alignment"),
                        number(element, "colspan", 1),
                        number(element, "rowspan", 1),
                        number(element, "weight", 0),
                        element.getAttribute("open").equals("true"),
                        attribute(element, "frame", "all"),
                        attribute(element, "grid", "all")
                ),
                number(element, "level", 0)
        );
    }

    private static String attribute(Element element, String name, String fallback) {
        return element.getAttribute(name).isEmpty() ? fallback : element.getAttribute(name);
    }

    private static int number(Element element, String name, int fallback) {
        try {
            return Math.clamp(Integer.parseInt(element.getAttribute(name)), 1, 1000);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean inline(Element element) {
        return List
            .of("strong", "em", "code", "link", "span", "strike", "callout", "br")
            .contains(element.getTagName());
    }

    private static void inlines(Element element, Style style, List<Span> spans) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && inline(child)) {
                if (child.getTagName().equals("br")) {
                    spans.add(new Span("\n", style));
                    continue;
                }
                if (child.getTagName().equals("callout")) {
                    spans.add(Span.callout(child.getAttribute("number")));
                    continue;
                }
                Style next = switch (child.getTagName()) {
                    case "strong" -> style.bolded();
                    case "em" -> style.italicised();
                    case "code" -> style.coded();
                    case "strike" -> style.struck();
                    case "link" -> style.linked(child.getAttribute("target"));
                    default -> style;
                };
                inlines(child, next, spans);
            } else if (node.getNodeType() == Node.TEXT_NODE) {
                spans.add(new Span(Controls.visible(node.getTextContent()), style));
            }
        }
    }

    @Override
    public void close() {
        if (engine != null) {
            engine.shutdown();
        }
    }
}

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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public final class AsciiDocReader implements AutoCloseable {
    private final Asciidoctor engine = Asciidoctor.Factory.create();
    private Path includeRoot;

    public AsciiDocReader() {
        engine.javaConverterRegistry().register(TerminalConverter.class);
        engine.javaExtensionRegistry()
                .includeProcessor(
                        new IncludeProcessor() {
                            @Override
                            public boolean handles(String target) {
                                return true;
                            }

                            @Override
                            public void process(
                                    org.asciidoctor.ast.Document document,
                                    PreprocessorReader reader,
                                    String target,
                                    Map<String, Object> attributes) {
                                try {
                                    Path path =
                                            Path.of(reader.getDir())
                                                    .resolve(target)
                                                    .normalize()
                                                    .toRealPath();
                                    if (!path.startsWith(includeRoot)) {
                                        throw new IOException(
                                                "Include is outside the document directory");
                                    }
                                    if (Files.size(path) > 4_000_000) {
                                        throw new IOException("Include exceeds 4 MB");
                                    }
                                    if (attributes.containsKey("tag")
                                            || attributes.containsKey("tags")
                                            || attributes.containsKey("lines")) {
                                        throw new IOException(
                                                "Include selectors are not supported yet");
                                    }
                                    reader.pushInclude(
                                            Files.readString(path),
                                            path.toString(),
                                            path.toString(),
                                            1,
                                            attributes);
                                } catch (IOException | java.nio.file.InvalidPathException e) {
                                    reader.pushInclude(
                                            "[include unavailable: "
                                                    + Controls.visible(target)
                                                    + "]",
                                            target,
                                            target,
                                            1,
                                            Map.of());
                                }
                            }
                        });
    }

    public Document read(String source, Path baseDirectory) {
        try {
            includeRoot = baseDirectory.toRealPath();
            String xml =
                    engine.convert(
                            Controls.visible(source),
                            Options.builder()
                                    .backend("glow-terminal")
                                    .safe(SafeMode.SAFE)
                                    .baseDir(baseDirectory.toFile())
                                    .toFile(false)
                                    .attributes(
                                            Attributes.builder()
                                                    .attribute("allow-uri-read", false)
                                                    .build())
                                    .build());
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var root =
                    factory.newDocumentBuilder()
                            .parse(new InputSource(new StringReader(xml)))
                            .getDocumentElement();
            return new Document(blocks(root), baseDirectory);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read AsciiDoc: " + e.getMessage(), e);
        }
    }

    private static List<Block> blocks(Element parent) {
        var blocks = new ArrayList<Block>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && !inline(element)) {
                blocks.add(block(element));
            }
        }
        return blocks;
    }

    private static Block block(Element element) {
        Block.Kind kind =
                switch (element.getTagName()) {
                    case "heading" -> Block.Kind.HEADING;
                    case "source" -> Block.Kind.CODE;
                    case "list" -> Block.Kind.LIST;
                    case "item" -> Block.Kind.ITEM;
                    case "table" -> Block.Kind.TABLE;
                    case "row" -> Block.Kind.ROW;
                    case "cell" -> Block.Kind.CELL;
                    case "quote" -> Block.Kind.QUOTE;
                    case "admonition" -> Block.Kind.ADMONITION;
                    case "image" -> Block.Kind.IMAGE;
                    case "diagram" -> Block.Kind.DIAGRAM;
                    default -> Block.Kind.PARAGRAPH;
                };
        var spans = new ArrayList<Span>();
        inlines(element, Style.PLAIN, spans);
        return new Block(
                kind,
                element.getAttribute("id"),
                spans,
                blocks(element),
                element.getAttribute("language"),
                element.getAttribute("target"));
    }

    private static boolean inline(Element element) {
        return List.of("strong", "em", "code", "link", "span").contains(element.getTagName());
    }

    private static void inlines(Element element, Style style, List<Span> spans) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && inline(child)) {
                Style next =
                        switch (child.getTagName()) {
                            case "strong" -> style.bolded();
                            case "em" -> style.italicised();
                            case "code" -> style.coded();
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
        engine.shutdown();
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.asciidoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.bric3.glowadoc.document.Block;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.Map;

class AsciiDocReaderTest {
    @TempDir
    Path directory;

    @TableTest(
            """
        kind     | bold
        document | '*bold*'
        document | '**bold**'
        section  | '*bold*'
        section  | '**bold**'
        yaml     | '*bold*'
        yaml     | '**bold**'
        """
    )
    void preservesInlineStylesInTitles(String kind, String bold) {
        String title = "plain " + bold + " _italic_ `code` [line-through]#gone#";
        String source = switch (kind) {
            case "document" -> "= " + title + "\n\nBody.";
            case "section" -> "== " + title + "\n\nBody.";
            case "yaml" -> "---\ntitle: '" + title + "'\n---\n\nBody.";
            default -> throw new IllegalArgumentException(kind);
        };
        try (var reader = new AsciiDocReader()) {
            var heading = reader
                .read(source, directory)
                .blocks()
                .stream()
                .filter(block -> block.kind() == Block.Kind.HEADING)
                .findFirst()
                .orElseThrow();
            assertEquals("plain bold italic code gone", heading.text());
            assertTrue(heading
                .spans()
                .stream()
                .anyMatch(span -> span.text().contains("bold") && span.style().bold()));
            assertTrue(heading
                .spans()
                .stream()
                .anyMatch(span -> span.text().contains("italic") && span.style().italic())
            );
            assertTrue(heading
                .spans()
                .stream()
                .anyMatch(span -> span.text().contains("code") && span.style().code()));
            assertTrue(heading
                .spans()
                .stream()
                .anyMatch(span -> span.text().contains("gone") && span.style().strike()));
        }
    }

    @TableTest(
            """
        documentTitle | introduction | headingCount
        false         | false        | 1
        false         | true         | 1
        true          | false        | 2
        true          | true         | 2
        """
    )
    void rendersOnlyExplicitDocumentTitles(boolean documentTitle, boolean introduction, int headingCount) {
        String source = (documentTitle ? "= Shared title\n\n" : "")
                + (introduction ? "An introduction.\n\n" : "")
                + "== Shared title\n\nBody.\n";
        try (var reader = new AsciiDocReader()) {
            var headings = reader
                .read(source, directory)
                .blocks()
                .stream()
                .filter(block -> block.kind() == Block.Kind.HEADING)
                .map(Block::text)
                .toList();
            assertEquals(Collections.nCopies(headingCount, "Shared title"), headings);
        }
    }

    @TableTest(
            """
        title                                      | expected
        'title: A plain title'                     | 'A plain title'
        'title: Section'                           | 'Section'
        'title: "A title: with a colon"'           | 'A title: with a colon'
        "title: 'A title with ''quotes'''"         | "A title with 'quotes'"
        'title: 42'                                | ''
        'title: [unexpected, list]'                | ''
        'title: {unexpected: map}'                 | ''
        'title: null'                              | ''
        'title: ""'                                | ''
        'title: [unterminated'                     | ''
        'title: !!java/object:java.lang.Object {}' | ''
        'description: No title'                    | ''
        ''                                         | ''
        """
    )
    void usesStringFrontMatterTitlesAndKeepsOtherMetadataReadable(String title, String expected) {
        String source = "---\n" + title + "\n---\n\nAn introduction.\n\n== Section\n\nBody.\n";
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(source, directory);
            var headings =
                    document
                .blocks()
                .stream()
                .filter(block -> block.kind() == Block.Kind.HEADING)
                .map(Block::text)
                .toList();
            assertEquals(expected.isEmpty() ? List.of("Section") : List.of(expected, "Section"), headings);
            assertEquals("front-matter", document.blocks().getFirst().format().variant());
            assertTrue(document
                .blocks()
                .stream()
                .anyMatch(block -> block.text().equals("An introduction.")));
        }
    }

    @TableTest(
            """
        enabled | explicitTitle | expected
        true    | false         | 'YAML title'
        false   | false         | ''
        true    | true          | 'AsciiDoc title'
        false   | true          | 'AsciiDoc title'
        """
    )
    void allowsDisablingFrontMatterTitlesAndPrefersAsciiDocTitles(
            boolean enabled,
            boolean explicitTitle,
            String expected
    ) {
        String source =
                "---\ntitle: YAML title\n---\n\n"
                + (explicitTitle ? "= AsciiDoc title\n\n" : "")
                + "== Section\n\nBody.\n";
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(source, directory, false, enabled, Map.of());
            var headings =
                    document
                .blocks()
                .stream()
                .filter(block -> block.kind() == Block.Kind.HEADING)
                .map(Block::text)
                .toList();
            assertEquals(expected.isEmpty() ? List.of("Section") : List.of(expected, "Section"), headings);
        }
    }

    @Test
    void decodesMultilineFrontMatterTitlesAndPreservesInlineFormatting() {
        String yaml = "---\ntitle: >-\n  A *bold* title\n  with `code` & café\n---";
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(yaml + "\n\n== Section\n\nBody.", directory);
            var heading = document.blocks().get(1);
            assertEquals(Block.Kind.HEADING, heading.kind());
            assertEquals("A bold title with code & café", heading.text());
            assertTrue(heading
                .spans()
                .stream()
                .anyMatch(span -> span.text().equals("bold") && span.style().bold()));
            assertTrue(heading
                .spans()
                .stream()
                .anyMatch(span -> span.text().equals("code") && span.style().code()));
            assertEquals(yaml, document.blocks().getFirst().text());
        }
    }

    @TableTest("""
        metadata
        aliases
        duplicates
        depth
        size
        empty
        """)
    void keepsTheBodyReadableWhenFrontMatterExceedsParsingLimits(String metadata) {
        String yaml = switch (metadata) {
            case "aliases" -> "title: Title\nitems: &items [1]\ncopy: *items\n";
            case "duplicates" -> "title: First\ntitle: Second\n";
            case "depth" -> "title: Title\nnested: " + "[".repeat(60) + "0" + "]".repeat(60) + "\n";
            case "size" -> "title: " + "x".repeat(256 * 1024) + "\n";
            case "empty" -> "";
            default -> throw new IllegalArgumentException(metadata);
        };
        try (var reader = new AsciiDocReader()) {
            var document = reader.read("---\n" + yaml + "---\n\n== Section\n\nBody.", directory);
            assertEquals(
                    List.of("Section"),
                    document
                        .blocks()
                        .stream()
                        .filter(block -> block.kind() == Block.Kind.HEADING)
                        .map(Block::text)
                        .toList()
            );
            assertTrue(document
                .blocks()
                .stream()
                .anyMatch(block -> block.text().equals("Body.")));
        }
    }

    @Test
    void makesDecodedYamlControlsVisible() {
        try (var reader = new AsciiDocReader()) {
            var document = reader.read("---\ntitle: \"Title\\e[31m\"\n---\n\nBody.", directory);
            var heading = document.blocks().get(1);
            assertEquals(Block.Kind.HEADING, heading.kind());
            assertTrue(heading.text().startsWith("Title"));
            assertFalse(heading.text().contains("\u001B"));
        }
    }

    @Test
    void preservesYamlFrontMatterAsLiteralSourceAndReflowsTheBody() {
        String yaml =
                """
                ---
                title: "An _unchanged_ title"
                tags:
                  - java
                  - terminal
                summary: |
                  First line.
                  Second line with {attributes} and {{< shortcode >}}.
                ---\
                """;
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(yaml + "\n\n= Heading\n\nA paragraph\nwith a soft break.\n", directory);
            var frontMatter = document.blocks().getFirst();
            assertEquals(Block.Kind.CODE, frontMatter.kind());
            assertEquals("yaml", frontMatter.language());
            assertEquals("front-matter", frontMatter.format().variant());
            assertEquals(yaml, frontMatter.text());
            assertEquals("Heading", document.blocks().get(1).text());
            assertEquals("A paragraph with a soft break.", document.blocks().get(2).text());
            var crlf = reader.read("﻿" + yaml.replace("\n", "\r\n") + "\r\n\r\nBody", directory);
            assertEquals(yaml, crlf.blocks().getFirst().text());
            var terminated = reader.read("---\ntitle: Title\n...", directory);
            assertEquals("---\ntitle: Title\n...", terminated.blocks().getFirst().text());
        }
    }

    @Test
    void requiresClosedFrontMatterAtTheStartOfTheDocument() {
        try (var reader = new AsciiDocReader()) {
            for (String source : new String[] {"---\ntitle: Unclosed\n", "Paragraph\n\n---\ntitle: Body\n---"}) {
                assertTrue(reader
                    .read(source, directory)
                    .blocks()
                    .stream()
                    .noneMatch(block -> block.language().equals("yaml"))
                );
            }
        }
    }

    @Test
    void convertsBlocksAndNestedInlineStyles() {
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(
                    "= Title\n\n"
                    + "A *bold _nested_* & <literal> https://example.org[link].\n\n"
                    + "[source,java]\n"
                    + "----\n"
                    + "a < b && b > c\n"
                    + "----\n",
                    directory
            );
            assertEquals("Title", document.blocks().getFirst().text());
            var paragraph = document.blocks().get(1);
            assertEquals("A bold nested & <literal> link.", paragraph.text());
            assertTrue(paragraph
                .spans()
                .stream()
                .anyMatch(s -> s.text().equals("nested") && s.style().italic() && s.style().bold())
            );
            assertEquals("a < b && b > c", document.blocks().get(2).text());
        }
    }

    @Test
    void convertsTablesListsAndDiagramSource() {
        try (var reader = new AsciiDocReader()) {
            var doc = reader.read(
                    "* Item\n"
                    + "** Nested\n\n"
                    + "term:: Meaning\n\n"
                    + "|===\n"
                    + "|A |B\n"
                    + "|===\n\n"
                    + "[mermaid]\n"
                    + "----\n"
                    + "flowchart LR\n"
                    + " A --> B\n"
                    + "----\n",
                    directory
            );
            assertEquals(Block.Kind.LIST, doc.blocks().getFirst().kind());
            assertTrue(doc
                .blocks()
                .stream()
                .anyMatch(b -> b.kind() == Block.Kind.TABLE && b.children().getFirst().children().size() == 2)
            );
            assertTrue(doc
                .blocks()
                .stream()
                .anyMatch(b -> b.kind() == Block.Kind.DIAGRAM
                        && b.language().equals("mermaid")
                        && b.text().contains("A --> B"))
            );
        }
    }

    @TableTest(
            """
        language | delimiter
        plantuml | '----'
        plantuml | '....'
        mermaid  | '----'
        mermaid  | '....'
        ditaa    | '----'
        ditaa    | '....'
        """
    )
    void preservesDiagramSource(String language, String delimiter) {
        String source = "  +-------------+\n  | A < B & cGRE|\n  +-------------+";
        try (var reader = new AsciiDocReader()) {
            var document = reader.read("[" + language + "]\n" + delimiter + "\n" + source + "\n" + delimiter, directory);
            var block = document.blocks().getFirst();
            assertEquals(Block.Kind.DIAGRAM, block.kind());
            assertEquals(language, block.language());
            assertEquals(source, block.text());
        }
    }

    @Test
    void includesLocalFilesAndRejectsParentTraversal() throws Exception {
        Files.writeString(directory.resolve("local.adoc"), "Local content");
        Path parent = directory.getParent().resolve("outside-glow-test.adoc");
        Files.writeString(parent, "PRIVATE OUTSIDE CONTENT");
        Files.createSymbolicLink(directory.resolve("symlink.adoc"), parent);
        try (var reader = new AsciiDocReader()) {
            var doc = reader.read(
                    "include::local.adoc[]\n\n" + "include::../outside-glow-test.adoc[]\n\n" + "include::symlink.adoc[]",
                    directory
            );
            String text = doc.blocks().toString();
            assertTrue(text.contains("Local content"));
            assertFalse(text.contains("PRIVATE OUTSIDE CONTENT"));
        } finally {
            Files.deleteIfExists(parent);
        }
    }
}

// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.asciidoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bric3.glowadoc.document.Block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class AsciiDocReaderTest {
    @TempDir Path directory;

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
            var document =
                    reader.read(
                            yaml + "\n\n= Heading\n\nA paragraph\nwith a soft break.\n", directory);
            var frontMatter = document.blocks().getFirst();
            assertEquals(Block.Kind.CODE, frontMatter.kind());
            assertEquals("yaml", frontMatter.language());
            assertEquals(yaml, frontMatter.text());
            assertEquals("Heading", document.blocks().get(1).text());
            assertEquals("A paragraph with a soft break.", document.blocks().get(2).text());
            var crlf =
                    reader.read("\ufeff" + yaml.replace("\n", "\r\n") + "\r\n\r\nBody", directory);
            assertEquals(yaml, crlf.blocks().getFirst().text());
            var terminated = reader.read("---\ntitle: Title\n...", directory);
            assertEquals("---\ntitle: Title\n...", terminated.blocks().getFirst().text());
        }
    }

    @Test
    void requiresClosedFrontMatterAtTheStartOfTheDocument() {
        try (var reader = new AsciiDocReader()) {
            for (String source :
                    new String[] {"---\ntitle: Unclosed\n", "Paragraph\n\n---\ntitle: Body\n---"}) {
                assertTrue(
                        reader.read(source, directory).blocks().stream()
                                .noneMatch(block -> block.language().equals("yaml")));
            }
        }
    }

    @Test
    void convertsBlocksAndNestedInlineStyles() {
        try (var reader = new AsciiDocReader()) {
            var document =
                    reader.read(
                            "= Title\n\n"
                                    + "A *bold _nested_* & <literal> https://example.org[link].\n\n"
                                    + "[source,java]\n"
                                    + "----\n"
                                    + "a < b && b > c\n"
                                    + "----\n",
                            directory);
            assertEquals("Title", document.blocks().getFirst().text());
            var paragraph = document.blocks().get(1);
            assertEquals("A bold nested & <literal> link.", paragraph.text());
            assertTrue(
                    paragraph.spans().stream()
                            .anyMatch(s ->
                                    s.text().equals("nested")
                                            && s.style().italic()
                                            && s.style().bold()));
            assertEquals("a < b && b > c", document.blocks().get(2).text());
        }
    }

    @Test
    void convertsTablesListsAndDiagramSource() {
        try (var reader = new AsciiDocReader()) {
            var doc =
                    reader.read(
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
                            directory);
            assertEquals(Block.Kind.LIST, doc.blocks().getFirst().kind());
            assertTrue(
                    doc.blocks().stream()
                            .anyMatch(b ->
                                    b.kind() == Block.Kind.TABLE
                                            && b.children().getFirst().children().size() == 2));
            assertTrue(
                    doc.blocks().stream()
                            .anyMatch(b ->
                                    b.kind() == Block.Kind.DIAGRAM
                                            && b.language().equals("mermaid")
                                            && b.text().contains("A --> B")));
        }
    }

    @Test
    void includesLocalFilesAndRejectsParentTraversal() throws Exception {
        Files.writeString(directory.resolve("local.adoc"), "Local content");
        Path parent = directory.getParent().resolve("outside-glow-test.adoc");
        Files.writeString(parent, "PRIVATE OUTSIDE CONTENT");
        Files.createSymbolicLink(directory.resolve("symlink.adoc"), parent);
        try (var reader = new AsciiDocReader()) {
            var doc =
                    reader.read(
                            "include::local.adoc[]\n\n"
                                    + "include::../outside-glow-test.adoc[]\n\n"
                                    + "include::symlink.adoc[]",
                            directory);
            String text = doc.blocks().toString();
            assertTrue(text.contains("Local content"));
            assertFalse(text.contains("PRIVATE OUTSIDE CONTENT"));
        } finally {
            Files.deleteIfExists(parent);
        }
    }
}

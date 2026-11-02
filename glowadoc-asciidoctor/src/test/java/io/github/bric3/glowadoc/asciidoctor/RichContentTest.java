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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RichContentTest {
    @Test
    void reflowsProseAndLinkedSuperscriptsButPreservesExplicitBreaks() {
        try (var reader = new AsciiDocReader()) {
            var paragraphs = reader
                .read(
                        """
                                    https://example.org/runtime[Runtime 27] arrives in September
                                    with an _updated_ API.

                                    The constant
                                    https://example.org/one[^1^]
                                    https://example.org/two[^2^]
                                    https://example.org/three[^3^]
                                    fits an integer.

                                    first +
                                    second

                                    [%hardbreaks]
                                    third
                                    fourth

                                    [verse]
                                    ____
                                    fifth
                                    sixth
                                    ____

                                    ++++
                                    <p>HTML
                                    <a href="https://example.org/reference"><sup>4</sup></a><br>next</p>
                                    ++++
                                    """,
                        directory
                )
                .blocks();
            assertEquals("Runtime 27 arrives in September with an updated API.", paragraphs.get(0).text());
            assertEquals("The constant [1] [2] [3] fits an integer.", paragraphs.get(1).text());
            assertEquals(
                    List.of("[1]", "[2]", "[3]"),
                    paragraphs
                        .get(1)
                        .spans()
                        .stream()
                        .filter(s -> !s.style().link().isEmpty())
                        .map(s -> s.text())
                        .toList()
            );
            assertEquals("first\nsecond", paragraphs.get(2).text());
            assertEquals("third\nfourth", paragraphs.get(3).text());
            assertEquals("fifth\nsixth", paragraphs.get(4).text());
            assertEquals("HTML [4]\nnext", paragraphs.get(5).text());
        }
    }

    @Test
    void preservesCalloutNumbersAndLiteralCode() {
        StringBuilder source = new StringBuilder(
                ":source-highlighter: rouge\n\n" + "[source,java]\n" + "----\n" + "var literal = \"<1>\";\n" + "// \\<1>\n"
        );
        for (int i = 1; i <= 11; i++) {
            source.append("int value").append(i).append(" = ").append(i).append("; // <").append(i).append(">\n");
        }
        source.append("----\n");
        for (int i = 1; i <= 11; i++) {
            source.append("<").append(i).append("> Explanation ").append(i).append('\n');
        }
        try (var reader = new AsciiDocReader()) {
            var blocks = reader.read(source.toString(), directory).blocks();
            var code = blocks.getFirst();
            assertEquals(Block.Kind.CODE, code.kind());
            assertTrue(code.text().contains("var literal = \"<1>\";"), code.text());
            assertEquals(11, code
                .spans()
                .stream()
                .filter(span -> !span.callout().isEmpty())
                .count());
            var numbers = all(blocks)
                .flatMap(block -> block.spans().stream())
                .filter(span -> !span.callout().isEmpty())
                .map(span -> span.callout())
                .toList();
            assertEquals(22, numbers.size(), numbers.toString());
            assertEquals(2, numbers.stream().filter("11"::equals).count(), numbers.toString());
        }
        try (var reader = new AsciiDocReader()) {
            var block = reader.read("[source,java,subs=none]\n----\n// <1>\n----\n", directory).blocks().getFirst();
            assertEquals("// <1>", block.text());
            assertTrue(block
                .spans()
                .stream()
                .allMatch(span -> span.callout().isEmpty()));
            var onlyCallouts = reader
                .read(
                        "[source,java,subs=callouts]\n" + "----\n" + "List<String> values; // <1>\n" + "----\n",
                        directory
                )
                .blocks()
                .getFirst();
            assertTrue(onlyCallouts.text().contains("List<String> values;"), onlyCallouts.text());
            assertTrue(onlyCallouts
                .spans()
                .stream()
                .anyMatch(span -> span.callout().equals("1")));
        }
    }

    @Test
    void preservesStyledCaptionsAndTableBorderMetadata() {
        String source =
                """
                = Logging
                :table-frame: none
                :table-grid: rows

                .`java` command
                [source,shell]
                ----
                printf '<code>literal</code>'
                ----

                |===
                |a |b
                |c |d
                |===

                [frame=ends,grid=cols,options="header,footer"]
                |===
                |Name |Value
                |A |1
                |Total |1
                |===
                """;
        try (var reader = new AsciiDocReader()) {
            var blocks = reader.read(source, directory).blocks();
            var code = blocks
                .stream()
                .filter(b -> b.kind() == Block.Kind.CODE)
                .findFirst()
                .orElseThrow();
            assertEquals("java command", code.format().title());
            assertTrue(code
                .format()
                .titleSpans()
                .stream()
                .anyMatch(s -> s.text().equals("java") && s.style().code()));
            assertEquals("printf '<code>literal</code>'", code.text());
            var tables = blocks
                .stream()
                .filter(b -> b.kind() == Block.Kind.TABLE)
                .toList();
            assertEquals("none", tables.getFirst().format().frame());
            assertEquals("rows", tables.getFirst().format().grid());
            assertEquals("ends", tables.getLast().format().frame());
            assertEquals("cols", tables.getLast().format().grid());
            assertEquals("header", tables.getLast().children().getFirst().format().variant());
            assertEquals("footer", tables.getLast().children().getLast().format().variant());
        }
    }

    @TempDir
    Path directory;

    @Test
    void preservesGroupedBadgeImagesAndTheirLinks() {
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(
                    """
                    image:https://example.org/build.svg?branch=main&style=flat[Build,link=https://example.org/actions]
                    image:https://img.shields.io/badge/license-MPL%2D%2D2.0-blue[Licence,link=LICENSE]
                    image:https://example.org/version[Release,link=https://example.org/releases]
                    """,
                    directory
            );
            var images = all(document.blocks())
                .filter(block -> block.kind() == Block.Kind.IMAGE)
                .toList();
            assertEquals(3, images.size());
            assertEquals("https://example.org/build.svg?branch=main&style=flat", images.getFirst().target());
            assertEquals("Build", images.getFirst().text());
            assertEquals("https://example.org/actions", images.getFirst().spans().getFirst().style().link());
            assertEquals("https://img.shields.io/badge/license-MPL%2D%2D2.0-blue", images.get(1).target());
            assertEquals("LICENSE", images.get(1).spans().getFirst().style().link());
            assertEquals("https://example.org/version", images.getLast().target());
        }
    }

    @Test
    void appliesExternalAttributesAndPreservesStandaloneInlineImageTargets() {
        try (var reader = new AsciiDocReader()) {
            var document = reader.read(
                    "= Images\n:image-assets: document\n\n"
                    + "image:{image-assets}/graph.png[Flamegraph]\n\n"
                    + "image::block.png[Block]\n\n"
                    + "Before image:icon.png[Icon] after.\n\n"
                    + "{literal}\n",
                    directory,
                    false,
                    Map.of("image-assets", "assets", "imagesdir", "pictures", "literal", "a,b=c")
            );
            var images = all(document.blocks())
                .filter(block -> block.kind() == Block.Kind.IMAGE)
                .toList();
            assertEquals(2, images.size());
            assertEquals("pictures/assets/graph.png", images.getFirst().target());
            assertEquals("Flamegraph", images.getFirst().text());
            assertEquals("pictures/block.png", images.getLast().target());
            String text = all(document.blocks()).map(Block::text).collect(Collectors.joining("\n"));
            assertTrue(text.contains("Before [image: Icon] after."), text);
            assertTrue(text.contains("a,b=c"), text);
            var controls = reader.read(
                    "image::{image-assets}/graph.png[]",
                    directory,
                    false,
                    Map.of("image-assets", "\u001B]52;c;payload\u0007")
            );
            var target =
                    all(controls.blocks())
                .filter(block -> block.kind() == Block.Kind.IMAGE)
                .findFirst()
                .orElseThrow()
                .target();
            assertFalse(target.contains("\u001B"), target);
            assertTrue(target.contains("^[]52;c;payload"), target);
            var again = reader.read("image:{image-assets}/graph.png[]", directory);
            assertFalse(
                    all(again.blocks())
                        .anyMatch(block -> block.target().contains("assets/graph.png")
                        && !block.target().contains("{image-assets}")),
                    "Attributes must not leak between reads"
            );
        }
    }

    private static Stream<Block> all(List<Block> blocks) {
        return blocks
            .stream()
            .flatMap(b -> Stream.concat(Stream.of(b), all(b.children())));
    }

    @Test
    void filtersOnlyHugoTagsAndRetainsExamplesAndIncludes() throws Exception {
        String source =
                "Before {{< box text=\">}}\" >}}inside{{< /box >}} after {{ generic }}.\n\n"
                + "`{{< code >}}`\n\n"
                + "----\n"
                + "{{% literal %}}\n"
                + "----\n\n"
                + "include::part.adoc[]";
        Files.writeString(directory.resolve("part.adoc"), "Included {{% tag %}}body");
        try (var reader = new AsciiDocReader()) {
            String hidden =
                    all(reader.read(source, directory).blocks()).map(Block::text).collect(Collectors.joining("\n"));
            assertTrue(hidden.contains("Before inside after {{ generic }}."), hidden);
            assertTrue(hidden.contains("{{< code >}}"), hidden);
            assertTrue(hidden.contains("{{% literal %}}"), hidden);
            assertTrue(hidden.contains("Included body"), hidden);
            String shown =
                    all(reader.read(source, directory, true).blocks())
                .map(Block::text)
                .collect(Collectors.joining("\n"));
            assertTrue(shown.contains("{{< box"), shown);
            assertTrue(shown.contains("{{% tag %}}"), shown);
        }
        assertEquals("a x b", Shortcodes.hide("a {{< x\n key=\"v\" >}}x{{< /x >}} b"));
        assertEquals("a {{< unfinished", Shortcodes.hide("a {{< unfinished"));
        assertEquals("  {{< literal >}}", Shortcodes.hide("  {{< literal >}}"));
    }

    @Test
    void retainsBlockMetadataAndStrikeThrough() {
        String source =
                """
                .More
                [%collapsible%open]
                ====
                [line-through]#removed#
                ====

                WARNING: Danger

                [cols="1,>2",options="header"]
                |===
                |Name |Value
                |one |*bold*
                |===
                """;
        try (var reader = new AsciiDocReader()) {
            var blocks = all(reader.read(source, directory).blocks()).toList();
            var details = blocks
                .stream()
                .filter(b -> b.kind() == Block.Kind.DETAILS)
                .findFirst()
                .orElseThrow();
            assertTrue(details.format().open());
            assertEquals("More", details.format().title());
            assertTrue(blocks
                .stream()
                .flatMap(b -> b.spans().stream())
                .anyMatch(s -> s.style().strike()));
            assertTrue(blocks
                .stream()
                .anyMatch(b -> b.kind() == Block.Kind.ADMONITION && b.format().variant().equals("warning"))
            );
            assertTrue(blocks
                .stream()
                .anyMatch(b -> b.kind() == Block.Kind.CELL && b.format().alignment().equals("right"))
            );
            assertTrue(blocks
                .stream()
                .filter(b -> b.kind() == Block.Kind.CELL)
                .flatMap(b -> b.spans().stream())
                .anyMatch(span -> span.style().bold())
            );
            assertTrue(blocks
                .stream()
                .anyMatch(b -> b.kind() == Block.Kind.ROW && b.format().variant().equals("header"))
            );
        }
    }

    @Test
    void interpretsHtmlWithoutExecutingOrFetchingAnything() {
        String source =
                """
                Before +++<del>gone</del><br>line <img alt="alt">tail+++ after.

                ++++
                <details open="false"><summary>HTML details</summary>
                <p>Hello <strong>world</strong><br>Next</p>
                <table><tr><th>Name</th><th>Value</th></tr><tr><td>A</td><td>2</td></tr></table>
                <span style="text-decoration: line-through">deleted</span>
                <div style="text-decoration-line: line-through"><p>decorated</p></div>
                <script>SHOULD_NOT_RENDER</script>
                <style>HIDDEN_CSS</style>
                <pre>  &lt;source&gt;</pre>
                <a href="javascript:alert(1)">unsafe link</a>
                </details>
                ++++
                """;
        try (var reader = new AsciiDocReader()) {
            var blocks = all(reader.read(source, directory).blocks()).toList();
            String text = blocks.stream().map(Block::text).collect(Collectors.joining("\n"));
            assertFalse(text.contains("SHOULD_NOT_RENDER"), text);
            assertFalse(text.contains("HIDDEN_CSS"), text);
            assertTrue(text.contains("world"), text);
            assertTrue(text.contains("tail"), text);
            assertTrue(blocks
                .stream()
                .flatMap(b -> b.spans().stream())
                .anyMatch(s -> s.text().contains("decorated") && s.style().strike())
            );
            assertTrue(text.contains("  <source>"), text);
            assertTrue(blocks
                .stream()
                .anyMatch(b -> b.kind() == Block.Kind.DETAILS
                        && b.format().title().equals("HTML details")
                        && b.format().open())
            );
            assertTrue(blocks
                .stream()
                .flatMap(b -> b.spans().stream())
                .anyMatch(s -> s.text().equals("gone") && s.style().strike())
            );
        }
    }
}

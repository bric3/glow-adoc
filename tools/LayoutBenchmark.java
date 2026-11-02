// SPDX-License-Identifier: MPL-2.0
//JAVA 27
//DEPS org.openjdk.jmh:jmh-core:1.37
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.37
//JAVAC_OPTIONS -proc:full
package io.github.bric3.glowadoc.tools;

import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Block;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.render.Layout;
import io.github.bric3.glowadoc.render.RenderedLine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Run with jbang --cp PATH_TO_APPLICATION_JAR tools/LayoutBenchmark.java -prof gc.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(2)
public class LayoutBenchmark {
    @Param({"small", "typical", "large", "pathological"})
    public String size;
    @Param({"prose", "table"})
    public String content;
    private Document document;
    private Layout layout;

    @Setup
    public void setup() {
        int count = switch (size) {
            case "small" -> 4;
            case "typical" -> 40;
            case "large" -> 400;
            default -> 40;
        };
        String text = size.equals("pathological") ? "x".repeat(10_000) : "Unicode 👩‍💻 é and links to an API";
        var blocks = new ArrayList<Block>();
        for (int i = 0; i < count; i++) {
            var spans = List.of(new Span(text, Style.PLAIN.linked("https://github.com/owner/project/issues/42")));
            blocks.add(
                    content.equals("table")
                    ? new Block(
                            Block.Kind.ROW,
                            "",
                            List.of(),
                            List.of(
                                    new Block(Block.Kind.CELL, "", spans, List.of(), "", ""),
                                    Block.text(Block.Kind.CELL, "plain text")
                            ),
                            "",
                            ""
                    )
                    : new Block(Block.Kind.PARAGRAPH, "", spans, List.of(), "", "")
            );
        }
        document = new Document(
                content.equals("table") ? List.of(new Block(Block.Kind.TABLE, "", List.of(), blocks, "", "")) : blocks,
                Path.of(".")
        );
        layout = new Layout((source, language, appearance) -> List.of(new Span(source)));
    }

    @Benchmark
    public List<RenderedLine> render() {
        return layout.render(document, 120, Appearance.DARK);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}

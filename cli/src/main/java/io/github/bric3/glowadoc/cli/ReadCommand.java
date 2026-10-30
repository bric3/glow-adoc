// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import io.github.bric3.glowadoc.asciidoctor.AsciiDocReader;
import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.highlight.lumis.LumisHighlighter;
import io.github.bric3.glowadoc.pager.ReaderPager;
import io.github.bric3.glowadoc.render.Layout;
import io.github.bric3.glowadoc.terminal.Ansi;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.terminal.tty.TtyDetect;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@CommandDefinition(
        name = "glow-adoc",
        description = "Read AsciiDoc in the terminal",
        generateHelp = true)
public final class ReadCommand implements Command<CommandInvocation> {
    @Argument(description = "AsciiDoc file, or - for stdin")
    String file;

    @Option(name = "no-pager", hasValue = false, description = "Write directly to stdout")
    boolean noPager;

    @Option(description = "Output width in terminal cells (1-1000)")
    Integer width;

    @Option(name = "color", description = "auto, always or never")
    String colour;

    @Option(description = "auto, light or dark")
    String theme;

    @Option(description = "auto or never")
    String images;

    @Option(description = "TOML configuration file")
    String config;

    @Option(hasValue = false, description = "Show application version")
    boolean version;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        try {
            if (version) {
                String value = ReadCommand.class.getPackage().getImplementationVersion();
                System.out.println("glow-adoc " + (value == null ? "development" : value));
                return CommandResult.SUCCESS;
            }
            var settings = Configuration.load(config == null ? null : Path.of(config));
            String selectedColour =
                    Configuration.choice(
                            colour == null ? settings.colour() : colour,
                            "color",
                            Set.of("auto", "always", "never"));
            String selectedTheme =
                    Configuration.choice(
                            theme == null ? settings.theme() : theme,
                            "theme",
                            Set.of("auto", "light", "dark"));
            String selectedImages =
                    Configuration.choice(
                            images == null ? settings.images() : images,
                            "images",
                            Set.of("auto", "never"));
            int selectedWidth = width == null ? settings.width() : width;
            if (selectedWidth < 0 || selectedWidth > 1000 || (width != null && width == 0)) {
                throw new IllegalArgumentException("width must be between 1 and 1000");
            }
            boolean stdoutTty = TtyDetect.isStdoutTty();
            if (file == null && TtyDetect.isStdinTty()) {
                System.out.println(
                        "Usage: glow-adoc [--no-pager] [--width N] [--color auto|always|never]"
                                + " [--theme auto|light|dark] FILE|-\n"
                                + "Use --help for all options.");
                return CommandResult.USAGE_ERROR;
            }
            Path base = Path.of("").toAbsolutePath();
            String source;
            if (file == null || file.equals("-")) {
                source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                Path path = Path.of(file).toRealPath();
                base = path.getParent();
                source = Files.readString(path);
            }
            boolean useColour =
                    selectedColour.equals("always")
                            || (selectedColour.equals("auto")
                                    && stdoutTty
                                    && !System.getenv().containsKey("NO_COLOR")
                                    && !"dumb".equals(System.getenv("TERM")));
            boolean truecolour =
                    "truecolor".equals(System.getenv("COLORTERM"))
                            || "24bit".equals(System.getenv("COLORTERM"));
            Appearance appearance =
                    selectedTheme.equals("light") ? Appearance.LIGHT : Appearance.DARK;
            try (var reader = new AsciiDocReader();
                    var highlighter = new LumisHighlighter();
                    var diagrams =
                            new DiagramRenderer(
                                    settings.merman(),
                                    message -> System.err.println("glow-adoc: " + message))) {
                var document = reader.read(source, base);
                var layout =
                        new Layout(
                                useColour
                                        ? highlighter
                                        : (text, language, mode) ->
                                                java.util.List.of(new Span(text)));
                boolean pager =
                        !noPager
                                && settings.pager()
                                && stdoutTty
                                && !"dumb".equals(System.getenv("TERM"))
                                && !System.getProperty("os.name").startsWith("Windows");
                if (pager) {
                    new ReaderPager(
                                    document,
                                    layout,
                                    diagrams,
                                    useColour,
                                    truecolour,
                                    !selectedImages.equals("never"),
                                    selectedWidth)
                            .run(selectedTheme.equals("auto"), appearance);
                } else {
                    for (var line :
                            layout.render(
                                    document,
                                    selectedWidth == 0 ? 80 : selectedWidth,
                                    appearance)) {
                        System.out.println(Ansi.line(line.spans(), useColour, truecolour));
                        if (System.out.checkError()) {
                            break;
                        }
                    }
                }
            }
            return CommandResult.SUCCESS;
        } catch (IllegalArgumentException e) {
            System.err.println("glow-adoc: " + Controls.visible(e.getMessage()));
            return CommandResult.USAGE_ERROR;
        } catch (Exception e) {
            System.err.println("glow-adoc: " + Controls.visible(String.valueOf(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}

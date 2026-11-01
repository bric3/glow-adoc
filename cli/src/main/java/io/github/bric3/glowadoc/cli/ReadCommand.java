// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import io.github.bric3.glowadoc.asciidoctor.AsciiDocReader;
import io.github.bric3.glowadoc.diagrams.DiagramRenderer;
import io.github.bric3.glowadoc.document.Appearance;
import io.github.bric3.glowadoc.document.Controls;
import io.github.bric3.glowadoc.document.Document;
import io.github.bric3.glowadoc.document.Span;
import io.github.bric3.glowadoc.document.Style;
import io.github.bric3.glowadoc.highlight.DeferredHighlighter;
import io.github.bric3.glowadoc.highlight.lumis.LumisHighlighter;
import io.github.bric3.glowadoc.pager.ReaderPager;
import io.github.bric3.glowadoc.render.ContentArea;
import io.github.bric3.glowadoc.render.Layout;
import io.github.bric3.glowadoc.render.Palette;
import io.github.bric3.glowadoc.render.RenderOptions;
import io.github.bric3.glowadoc.terminal.Ansi;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.command.option.OptionVisibility;
import org.aesh.terminal.tty.TerminalBuilder;
import org.aesh.terminal.tty.TtyDetect;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Option(
            name = "no-cache",
            hasValue = false,
            description = "Bypass conversion and syntax highlighting disk caches")
    boolean noCache;

    @Option(description = "Output width in terminal cells (1-1000)")
    Integer width;

    @Option(name = "color", description = "auto, always or never")
    String colour;

    @Option(description = "auto, light or dark")
    String theme;

    @Option(description = "auto or never")
    String images;

    @Option(description = "hide or show Hugo shortcode tags")
    String shortcodes;

    @Option(description = "unicode, nerd or ascii glyphs")
    String icons;

    @Option(name = "table-stripes", description = "true or false: alternate table row backgrounds")
    String tableStripes;

    @Option(description = "true or false: centre the content area")
    String center;

    @Option(
            name = "page-background",
            description = "true or false: fill the page background (default false)")
    String pageBackground;

    @Option(description = "true or false: enable pager mouse input (default true)")
    String mouse;

    @Option(name = "table-frame", description = "auto, all, sides, ends or none")
    String tableFrame;

    @Option(name = "table-grid", description = "auto, all, rows, cols or none")
    String tableGrid;

    @Option(name = "table-section-borders", description = "thin or heavy header/footer separators")
    String tableSectionBorders;

    @Option(description = "TOML configuration file")
    String config;

    @OptionList(
            name = "attribute",
            shortName = 'a',
            valueSeparator = '\0',
            description = "AsciiDoc name=value attribute (repeatable)")
    List<String> attributes;

    @OptionList(
            name = "image-root",
            valueSeparator = '\0',
            description = "Additional image directory (repeatable; relative to working directory)")
    List<String> imageRoots;

    @Option(hasValue = false, description = "Show application version")
    boolean version;

    @Option(hasValue = false, description = "Write cumulative startup stage timings to stderr")
    boolean timings;

    @Option(
            name = "train-aot",
            hasValue = false,
            description = "Launcher only: --train-aot [FILE] creates a local startup cache")
    boolean trainAot;

    @Option(name = "aot-workload", hasValue = false, visibility = OptionVisibility.HIDDEN)
    boolean aotWorkload;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        long started = System.nanoTime();
        try (var workload = aotWorkload ? AotWorkload.open() : null) {
            if (workload != null) {
                noPager = true;
                noCache = true;
            }
            if (trainAot) {
                throw new IllegalArgumentException(
                        "Use the executable launcher: glow-adoc --train-aot [FILE]");
            }
            if (version) {
                String value = ReadCommand.class.getPackage().getImplementationVersion();
                System.out.println("glow-adoc " + (value == null ? "development" : value));
                return CommandResult.SUCCESS;
            }
            Path input =
                    workload != null
                            ? workload.document()
                            : file == null || file.equals("-") ? null : Path.of(file).toRealPath();
            Path base = input == null ? Path.of("").toAbsolutePath() : input.getParent();
            var settings =
                    aotWorkload
                            ? Configuration.defaults()
                            : Configuration.load(config == null ? null : Path.of(config), base);
            var cliAttributes = Configuration.attributes(Map.of(), attributes);
            var selectedAttributes = new LinkedHashMap<>(settings.attributes());
            selectedAttributes.putAll(cliAttributes);
            var selectedImageRoots = new ArrayList<>(settings.imageRoots());
            selectedImageRoots.addAll(Configuration.cliImageRoots(cliAttributes));
            if (imageRoots != null) {
                for (String root : imageRoots) {
                    selectedImageRoots.add(Path.of(root).toAbsolutePath().normalize());
                }
            }
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
            String selectedShortcodes =
                    Configuration.choice(
                            shortcodes == null ? settings.shortcodes() : shortcodes,
                            "shortcodes",
                            Set.of("hide", "show"));
            String selectedIcons =
                    Configuration.choice(
                            icons == null ? settings.icons() : icons,
                            "icons",
                            Set.of("unicode", "nerd", "ascii"));
            boolean selectedStripes =
                    tableStripes == null
                            ? settings.tableStripes()
                            : Boolean.parseBoolean(
                                    Configuration.choice(
                                            tableStripes,
                                            "table-stripes",
                                            Set.of("true", "false")));
            boolean selectedCenter =
                    center == null
                            ? settings.center()
                            : Boolean.parseBoolean(
                                    Configuration.choice(
                                            center, "center", Set.of("true", "false")));
            if (selectedWidth < 0 || selectedWidth > 1000 || (width != null && width == 0)) {
                throw new IllegalArgumentException("width must be between 1 and 1000");
            }
            boolean selectedPageBackground =
                    pageBackground == null
                            ? settings.pageBackground()
                            : Boolean.parseBoolean(
                                    Configuration.choice(
                                            pageBackground,
                                            "page-background",
                                            Set.of("true", "false")));
            boolean selectedMouse =
                    mouse == null
                            ? settings.mouse()
                            : Boolean.parseBoolean(
                                    Configuration.choice(mouse, "mouse", Set.of("true", "false")));
            String selectedFrame =
                    Configuration.choice(
                            tableFrame == null ? settings.tableFrame() : tableFrame,
                            "table-frame",
                            Set.of("auto", "all", "sides", "ends", "none"));
            String selectedGrid =
                    Configuration.choice(
                            tableGrid == null ? settings.tableGrid() : tableGrid,
                            "table-grid",
                            Set.of("auto", "all", "rows", "cols", "none"));
            String selectedSectionBorders =
                    Configuration.choice(
                            tableSectionBorders == null
                                    ? settings.tableSectionBorders()
                                    : tableSectionBorders,
                            "table-section-borders",
                            Set.of("thin", "heavy"));
            boolean stdoutTty = TtyDetect.isStdoutTty();
            if (!aotWorkload && file == null && TtyDetect.isStdinTty()) {
                System.out.println(
                        "Usage: glow-adoc [--no-pager] [--width N] [--color auto|always|never]"
                                + " [--theme auto|light|dark] FILE|-\n"
                                + "Use --help for all options.");
                return CommandResult.USAGE_ERROR;
            }
            String source;
            if (input == null) {
                source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                source = Files.readString(input);
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
            boolean pager =
                    !noPager
                            && settings.pager()
                            && stdoutTty
                            && !"dumb".equals(System.getenv("TERM"))
                            && !System.getProperty("os.name").startsWith("Windows");
            timing("config-input", started);
            Path conversionCache =
                    noCache || input == null
                            ? null
                            : Configuration.cachePath(
                                    System.getenv(),
                                    System.getProperty("user.home"),
                                    System.getProperty("os.name"));
            Path highlightCache =
                    conversionCache == null ? null : conversionCache.resolveSibling("highlights");
            try (var reader =
                            new AsciiDocReader(
                                    input, conversionCache, stage -> timing(stage, started));
                    var highlighter =
                            pager && useColour
                                    ? new DeferredHighlighter(new LumisHighlighter(highlightCache))
                                    : new LumisHighlighter(highlightCache);
                    var diagrams =
                            new DiagramRenderer(
                                    settings.merman(),
                                    message -> System.err.println("glow-adoc: " + message))) {
                var parsed =
                        reader.read(
                                source,
                                base,
                                selectedShortcodes.equals("show"),
                                selectedAttributes);
                timing("converted", started);
                var document =
                        new Document(parsed.blocks(), parsed.baseDirectory(), selectedImageRoots);
                var layout =
                        new Layout(
                                useColour
                                        ? highlighter
                                        : (text, language, mode) -> List.of(new Span(text)),
                                new RenderOptions(
                                        selectedIcons,
                                        selectedStripes,
                                        selectedPageBackground,
                                        selectedFrame,
                                        selectedGrid,
                                        selectedSectionBorders));
                if (pager) {
                    new ReaderPager(
                                    document,
                                    layout,
                                    diagrams,
                                    useColour,
                                    truecolour,
                                    !selectedImages.equals("never"),
                                    selectedWidth,
                                    selectedCenter,
                                    selectedPageBackground,
                                    selectedMouse,
                                    highlighter instanceof DeferredHighlighter deferred
                                            ? deferred
                                            : null,
                                    stage -> timing(stage, started))
                            .run(selectedTheme.equals("auto"), appearance);
                } else {
                    int viewport =
                            stdoutTty
                                    ? terminalWidth()
                                    : (selectedWidth > 0 ? selectedWidth + 1 : 81);
                    var area = ContentArea.within(viewport, selectedWidth, selectedCenter);
                    int contentWidth = area.width();
                    String margin = " ".repeat(area.margin());
                    if (selectedPageBackground) {
                        margin =
                                Ansi.line(
                                        List.of(
                                                new Span(
                                                        margin,
                                                        Style.PLAIN.colours(
                                                                "",
                                                                Palette.forAppearance(appearance)
                                                                        .canvas()))),
                                        useColour,
                                        truecolour);
                    }
                    for (var line : layout.render(document, contentWidth, appearance)) {
                        System.out.println(margin + Ansi.line(line.spans(), useColour, truecolour));
                        if (System.out.checkError()) {
                            break;
                        }
                    }
                    timing("output-complete", started);
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

    private void timing(String stage, long started) {
        if (timings) {
            System.err.printf(
                    Locale.ROOT,
                    "glow-adoc timing %s: %.1f ms%n",
                    stage,
                    (System.nanoTime() - started) / 1_000_000.0);
        }
    }

    private static int terminalWidth() {
        try (var terminal = TerminalBuilder.builder().system(true).build()) {
            int columns = terminal.getSize().getWidth();
            return columns > 0 ? columns : 81;
        } catch (Exception e) {
            return 81;
        }
    }
}

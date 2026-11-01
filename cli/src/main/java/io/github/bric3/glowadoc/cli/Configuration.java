// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import org.tomlj.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record Configuration(
        boolean pager,
        int width,
        String colour,
        String theme,
        String images,
        String merman,
        String shortcodes,
        String icons,
        boolean tableStripes,
        boolean center,
        boolean pageBackground,
        boolean mouse,
        String tableFrame,
        String tableGrid,
        String tableSectionBorders,
        Map<String, String> attributes,
        List<Path> imageRoots) {
    private static final Configuration DEFAULTS =
            new Configuration(
                    true,
                    0,
                    "auto",
                    "auto",
                    "auto",
                    "merman-cli",
                    "hide",
                    "nerd",
                    true,
                    false,
                    false,
                    true,
                    "auto",
                    "auto",
                    "thin",
                    Map.of(),
                    List.of());

    public Configuration {
        attributes = Map.copyOf(attributes);
        imageRoots = List.copyOf(imageRoots);
    }

    public static Configuration load(Path explicit) throws IOException {
        return load(explicit, Path.of(""));
    }

    public static Configuration load(Path explicit, Path directory) throws IOException {
        return load(
                explicit,
                directory,
                defaultPath(
                        System.getenv(),
                        System.getProperty("user.home"),
                        System.getProperty("os.name")));
    }

    static Configuration load(Path explicit, Path directory, Path userConfig) throws IOException {
        if (explicit != null) {
            return read(explicit, DEFAULTS, false);
        }
        Configuration settings =
                Files.exists(userConfig) ? read(userConfig, DEFAULTS, false) : DEFAULTS;
        Path project = projectPath(directory);
        return project == null ? settings : read(project, settings, true);
    }

    private static Path projectPath(Path directory) {
        for (Path parent = directory.toAbsolutePath().normalize();
                parent != null;
                parent = parent.getParent()) {
            for (String name : List.of(".glow-adoc.local.toml", ".glow-adoc.toml")) {
                Path candidate = parent.resolve(name);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Configuration read(Path path, Configuration defaults, boolean discovered)
            throws IOException {
        var toml = Toml.parse(path);
        if (toml.hasErrors()) {
            throw new IllegalArgumentException(
                    "Invalid configuration " + path + ": " + toml.errors().getFirst());
        }
        var allowed =
                Set.of(
                        "pager",
                        "width",
                        "color",
                        "theme",
                        "images",
                        "merman",
                        "shortcodes",
                        "icons",
                        "table-stripes",
                        "center",
                        "page-background",
                        "mouse",
                        "table-frame",
                        "table-grid",
                        "table-section-borders",
                        "attributes",
                        "image-roots");
        for (String key : toml.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown configuration key: " + key);
            }
        }
        // Automatic project discovery must not select a program to execute.
        if (discovered && toml.contains("merman")) {
            throw new IllegalArgumentException(
                    "Set merman in user configuration or an explicit --config file");
        }
        var attributes = new LinkedHashMap<>(defaults.attributes());
        if (toml.contains("attributes")) {
            var table = toml.getTable("attributes");
            for (String name : table.keySet()) {
                attributes.put(attributeName(name), table.getString(List.of(name)));
            }
        }
        var roots = new ArrayList<>(defaults.imageRoots());
        if (toml.contains("image-roots")) {
            roots.clear();
            Path parent = path.toAbsolutePath().getParent().toRealPath();
            var array = toml.getArray("image-roots");
            for (int i = 0; i < array.size(); i++) {
                Path root = parent.resolve(array.getString(i)).normalize();
                if (discovered
                        && (!root.startsWith(parent)
                                || Files.exists(root) && !root.toRealPath().startsWith(parent))) {
                    throw new IllegalArgumentException(
                            "Discovered image-roots must stay within " + parent);
                }
                roots.add(root);
            }
        }
        long width = toml.getLong("width", () -> (long) defaults.width());
        if (width < 0 || width > 1000) {
            throw new IllegalArgumentException("width must be between 0 and 1000");
        }
        return new Configuration(
                toml.getBoolean("pager", defaults::pager),
                (int) width,
                choice(
                        toml.getString("color", defaults::colour),
                        "color",
                        Set.of("auto", "always", "never")),
                choice(
                        toml.getString("theme", defaults::theme),
                        "theme",
                        Set.of("auto", "light", "dark")),
                choice(
                        toml.getString("images", defaults::images),
                        "images",
                        Set.of("auto", "never")),
                toml.getString("merman", defaults::merman),
                choice(
                        toml.getString("shortcodes", defaults::shortcodes),
                        "shortcodes",
                        Set.of("hide", "show")),
                choice(
                        toml.getString("icons", defaults::icons),
                        "icons",
                        Set.of("unicode", "nerd", "ascii")),
                toml.getBoolean("table-stripes", defaults::tableStripes),
                toml.getBoolean("center", defaults::center),
                toml.getBoolean("page-background", defaults::pageBackground),
                toml.getBoolean("mouse", defaults::mouse),
                choice(
                        toml.getString("table-frame", defaults::tableFrame),
                        "table-frame",
                        Set.of("auto", "all", "sides", "ends", "none")),
                choice(
                        toml.getString("table-grid", defaults::tableGrid),
                        "table-grid",
                        Set.of("auto", "all", "rows", "cols", "none")),
                choice(
                        toml.getString("table-section-borders", defaults::tableSectionBorders),
                        "table-section-borders",
                        Set.of("thin", "heavy")),
                attributes,
                roots);
    }

    static Map<String, String> attributes(Map<String, String> defaults, List<String> options) {
        var result = new LinkedHashMap<>(defaults);
        if (options != null) {
            for (String option : options) {
                String[] parts = option.split("=", 2);
                result.put(attributeName(parts[0]), parts.length == 1 ? "" : parts[1]);
            }
        }
        return result;
    }

    static List<Path> cliImageRoots(Map<String, String> attributes) {
        var roots = new ArrayList<Path>();
        for (String value : attributes.values()) {
            try {
                Path path = Path.of(value);
                if (path.isAbsolute() && Files.isDirectory(path)) {
                    roots.add(path.normalize());
                }
            } catch (InvalidPathException e) {
                // Attribute values can be arbitrary text.
            }
        }
        return List.copyOf(roots);
    }

    private static String attributeName(String name) {
        if (!name.matches("[A-Za-z0-9_][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid AsciiDoc attribute name: " + name);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    static String choice(String value, String option, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(option + " must be one of " + allowed);
        }
        return value;
    }

    public static Path defaultPath(Map<String, String> env, String home, String os) {
        String root =
                os.startsWith("Windows")
                        ? env.getOrDefault(
                                "APPDATA", Path.of(home, "AppData", "Roaming").toString())
                        : env.getOrDefault("XDG_CONFIG_HOME", Path.of(home, ".config").toString());
        if (root.isBlank()) {
            root =
                    Path.of(home, os.startsWith("Windows") ? "AppData/Roaming" : ".config")
                            .toString();
        }
        return Path.of(root).resolve("glow-adoc/config.toml");
    }

    static Path cachePath(Map<String, String> env, String home, String os) {
        Path fallback = Path.of(home, os.startsWith("Windows") ? "AppData/Local" : ".cache");
        String root =
                env.getOrDefault(os.startsWith("Windows") ? "LOCALAPPDATA" : "XDG_CACHE_HOME", "");
        try {
            return (root.isBlank() || !Path.of(root).isAbsolute() ? fallback : Path.of(root))
                    .resolve("glow-adoc/conversions");
        } catch (InvalidPathException e) {
            return fallback.resolve("glow-adoc/conversions");
        }
    }
}

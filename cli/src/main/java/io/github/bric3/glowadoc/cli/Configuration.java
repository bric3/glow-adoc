// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import org.tomlj.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public record Configuration(
        boolean pager, int width, String colour, String theme, String images, String merman) {
    public static Configuration load(Path explicit) throws IOException {
        Path path =
                explicit == null
                        ? defaultPath(
                                System.getenv(),
                                System.getProperty("user.home"),
                                System.getProperty("os.name"))
                        : explicit;
        if (explicit == null && !Files.exists(path)) {
            return new Configuration(true, 0, "auto", "auto", "auto", "merman-cli");
        }
        var toml = Toml.parse(path);
        if (toml.hasErrors()) {
            throw new IllegalArgumentException(
                    "Invalid configuration " + path + ": " + toml.errors().getFirst());
        }
        var allowed = Set.of("pager", "width", "color", "theme", "images", "merman");
        for (String key : toml.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown configuration key: " + key);
            }
        }
        long width = toml.getLong("width", () -> 0L);
        if (width < 0 || width > 1000) {
            throw new IllegalArgumentException("width must be between 0 and 1000");
        }
        return new Configuration(
                toml.getBoolean("pager", () -> true),
                (int) width,
                choice(
                        toml.getString("color", () -> "auto"),
                        "color",
                        Set.of("auto", "always", "never")),
                choice(
                        toml.getString("theme", () -> "auto"),
                        "theme",
                        Set.of("auto", "light", "dark")),
                choice(toml.getString("images", () -> "auto"), "images", Set.of("auto", "never")),
                toml.getString("merman", () -> "merman-cli"));
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
}

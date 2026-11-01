// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class ConfigurationTest {
    @TempDir Path directory;

    @Test
    void usesPlatformConfigPaths() {
        assertEquals(
                Path.of("/config/glow-adoc/config.toml"),
                Configuration.defaultPath(
                        Map.of("XDG_CONFIG_HOME", "/config"), "/home/me", "Linux"));
        assertEquals(
                Path.of("/home/me/AppData/Roaming/glow-adoc/config.toml"),
                Configuration.defaultPath(Map.of(), "/home/me", "Windows 11"));
    }

    @Test
    void readsValuesAndRejectsMalformedConfiguration() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "pager = false\nwidth = 40\ntheme = 'light'\n");
        assertFalse(Configuration.load(config).pager());
        assertEquals(40, Configuration.load(config).width());
        Files.writeString(
                config,
                "shortcodes = 'show'\nicons = 'ascii'\ntable-stripes = false\ncenter = true\n");
        var rich = Configuration.load(config);
        assertEquals("show", rich.shortcodes());
        assertEquals("ascii", rich.icons());
        assertFalse(rich.tableStripes());
        assertTrue(rich.center());
        Files.writeString(config, "icons = 'unknown'");
        assertThrows(IllegalArgumentException.class, () -> Configuration.load(config));
        Files.writeString(config, "width = -1");
        assertThrows(IllegalArgumentException.class, () -> Configuration.load(config));
        Files.writeString(config, "typo = true");
        assertThrows(IllegalArgumentException.class, () -> Configuration.load(config));
    }

    @Test
    void readsPresentationControlsAndValidatesTableChoices() throws Exception {
        Path config = directory.resolve("presentation.toml");
        Files.writeString(config, "");
        var defaults = Configuration.load(config);
        assertEquals("nerd", defaults.icons());
        assertFalse(defaults.pageBackground());
        assertTrue(defaults.mouse());
        assertEquals("auto", defaults.tableFrame());
        assertEquals("auto", defaults.tableGrid());
        Files.writeString(
                config,
                "page-background = true\n"
                        + "mouse = false\n"
                        + "table-frame = 'ends'\n"
                        + "table-grid = 'rows'\n"
                        + "table-section-borders = 'heavy'\n");
        var settings = Configuration.load(config);
        assertTrue(settings.pageBackground());
        assertFalse(settings.mouse());
        assertEquals("ends", settings.tableFrame());
        assertEquals("rows", settings.tableGrid());
        assertEquals("heavy", settings.tableSectionBorders());
        for (String key : List.of("table-frame", "table-grid", "table-section-borders")) {
            Files.writeString(config, key + " = 'invalid'");
            assertThrows(IllegalArgumentException.class, () -> Configuration.load(config));
        }
    }

    @Test
    void discoversTheNearestFileWithLocalTakingPriorityInTheSameDirectory() throws Exception {
        Path user = directory.resolve("user.toml");
        Files.writeString(user, "width = 60\ntheme = 'light'\n[attributes]\nfrom-user = 'kept'\n");
        Path project = Files.createDirectory(directory.resolve("project"));
        Path content = Files.createDirectory(project.resolve("content"));
        Path posts = Files.createDirectory(content.resolve("posts"));
        Files.writeString(project.resolve(".glow-adoc.local.toml"), "width = 80\n");
        Files.writeString(
                content.resolve(".glow-adoc.toml"),
                "width = 40\ntheme = 'dark'\n[attributes]\nname = 'project'\n");
        var nearest = Configuration.load(null, posts, user);
        assertEquals(40, nearest.width());
        assertEquals("dark", nearest.theme());
        assertEquals(Map.of("name", "project", "from-user", "kept"), nearest.attributes());

        Files.writeString(
                content.resolve(".glow-adoc.local.toml"),
                "width = 30\n[attributes]\nname = 'local'\n");
        var local = Configuration.load(null, posts, user);
        assertEquals(30, local.width());
        assertEquals("light", local.theme(), "Only the first project file is loaded");
        assertEquals("local", local.attributes().get("name"));

        Path explicit = directory.resolve("explicit.toml");
        Files.writeString(explicit, "width = 20\n");
        var selected = Configuration.load(explicit, posts, user);
        assertEquals(20, selected.width());
        assertEquals("auto", selected.theme());
        assertTrue(selected.attributes().isEmpty());
    }

    @Test
    void usesDefaultsWhenConfigurationIsAbsentAndReportsInvalidNearestFiles() throws Exception {
        Path absentUser = directory.resolve("absent.toml");
        assertEquals(0, Configuration.load(null, directory, absentUser).width());
        Path config = directory.resolve(".glow-adoc.local.toml");
        Files.writeString(config, "width = 'broken'\n");
        Files.writeString(directory.resolve(".glow-adoc.toml"), "width = 20\n");
        assertThrows(RuntimeException.class, () -> Configuration.load(null, directory, absentUser));
        Files.writeString(config, "merman = './untrusted-program'\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> Configuration.load(null, directory, absentUser));
    }

    @Test
    void mergesLiteralAttributesWithTheLastCliValueWinning() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "[attributes]\nimage-assets = 'assets'\nname = 'config'\n");
        var settings = Configuration.load(config);
        assertEquals(Map.of("image-assets", "assets", "name", "config"), settings.attributes());
        assertEquals(
                Map.of("image-assets", "assets", "name", "cli", "literal", "a,b=c", "flag", ""),
                Configuration.attributes(
                        settings.attributes(),
                        List.of("name=first", "name=cli", "literal=a,b=c", "flag")));
        assertThrows(
                IllegalArgumentException.class,
                () -> Configuration.attributes(Map.of(), List.of("=missing-name")));
        Files.writeString(config, "[attributes]\nname = 42\n");
        assertThrows(RuntimeException.class, () -> Configuration.load(config));
    }

    @Test
    void resolvesImageRootsFromTheConfigDirectoryAndBoundsDiscoveredRoots() throws Exception {
        Path project = Files.createDirectory(directory.resolve("site"));
        Path assets = Files.createDirectory(project.resolve("static"));
        Path config = project.resolve(".glow-adoc.toml");
        Files.writeString(config, "image-roots = ['static']\n");
        var settings = Configuration.load(null, project, directory.resolve("absent.toml"));
        assertEquals(List.of(assets.toRealPath()), settings.imageRoots());
        Files.writeString(config, "image-roots = ['..']\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> Configuration.load(null, project, directory.resolve("absent.toml")));
        assertEquals(
                List.of(directory.toRealPath()),
                Configuration.load(config).imageRoots(),
                "Explicit configuration can allow directories outside the project");
        Files.createSymbolicLink(project.resolve("escape"), directory);
        Files.writeString(config, "image-roots = ['escape']\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> Configuration.load(null, project, directory.resolve("absent.toml")));
    }

    @Test
    void absoluteCliDirectoryAttributesAllowTheirImages() throws Exception {
        Path assets = Files.createDirectory(directory.resolve("assets"));
        Path file = Files.writeString(directory.resolve("not-a-directory"), "text");
        var attributes =
                Configuration.attributes(
                        Map.of(),
                        List.of(
                                "image-assets=" + assets,
                                "file=" + file,
                                "relative=assets",
                                "missing=" + directory.resolve("missing"),
                                "text=not\u0000a\u0000path"));
        assertEquals(List.of(assets), Configuration.cliImageRoots(attributes));
        var overwritten =
                Configuration.attributes(
                        Map.of(), List.of("image-assets=" + assets, "image-assets=literal"));
        assertTrue(Configuration.cliImageRoots(overwritten).isEmpty());
    }
}

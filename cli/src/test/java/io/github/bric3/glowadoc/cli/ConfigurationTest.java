// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
        Files.writeString(config, "width = -1");
        assertThrows(IllegalArgumentException.class, () -> Configuration.load(config));
        Files.writeString(config, "typo = true");
        assertThrows(IllegalArgumentException.class, () -> Configuration.load(config));
    }
}

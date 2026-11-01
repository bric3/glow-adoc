// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.asciidoctor;

import io.github.bric3.glowadoc.cache.CacheFiles;

import org.asciidoctor.Asciidoctor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** One private, replaceable conversion per input file. */
final class ConversionCache {
    private final CacheFiles files;
    private final String slot;
    private final String implementation;

    ConversionCache(Path input, Path directory, String implementation) {
        files = new CacheFiles(directory);
        slot = input.toAbsolutePath().normalize().toString();
        this.implementation = implementation;
    }

    static ConversionCache open(Path input, Path directory) {
        if (input == null || directory == null || !Files.isRegularFile(input)) {
            return null;
        }
        String implementation =
                CacheFiles.artifactIdentity(AsciiDocReader.class, Asciidoctor.class);
        return implementation == null
                ? null
                : new ConversionCache(input, directory, implementation);
    }

    String key(String source, Path base, boolean showShortcodes, Map<String, String> attributes) {
        // Time-dependent substitutions must be evaluated by Asciidoctor on every read.
        if (temporal(source) || attributes.values().stream().anyMatch(ConversionCache::temporal)) {
            return null;
        }
        var values =
                new ArrayList<>(
                        List.of(
                                implementation,
                                Runtime.version().toString(),
                                System.getProperty("user.home"),
                                Locale.getDefault().toLanguageTag(),
                                ZoneId.systemDefault().getId(),
                                base.toString(),
                                Boolean.toString(showShortcodes),
                                source));
        for (String name : List.of("TZ", "LANG", "LC_ALL", "LC_TIME", "SOURCE_DATE_EPOCH")) {
            values.add(System.getenv().getOrDefault(name, ""));
        }
        new TreeMap<>(attributes)
                .forEach(
                        (name, value) -> {
                            values.add(name);
                            values.add(value);
                        });
        return CacheFiles.key(values);
    }

    String read(String key) {
        byte[] value = files.read(slot, key);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    void write(String key, String xml) {
        files.write(slot, key, xml.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean temporal(String text) {
        return List.of("localdate", "localtime", "localyear", "docdate", "doctime", "docyear")
                .stream()
                .anyMatch(text::contains);
    }
}

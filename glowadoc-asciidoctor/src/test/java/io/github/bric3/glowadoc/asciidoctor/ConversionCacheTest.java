// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.asciidoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ConversionCacheTest {
    @TempDir
    Path directory;

    @BeforeEach
    void canonicalDirectory() throws Exception {
        directory = directory.toRealPath();
    }

    @Test
    void cacheHitsPreserveYamlFrontMatterWithoutDuplicatingIt() {
        var cache = new ConversionCache(directory.resolve("post.adoc"), directory.resolve("cache"), "front-matter");
        String source = "---\ntitle: Title\ntags:\n  - java\n---\n\nBody\n";
        var stages = new ArrayList<String>();
        try (var first = new AsciiDocReader(cache, ignored -> {})) {
            var expected = first.read(source, directory);
            try (var next = new AsciiDocReader(cache, stages::add)) {
                assertEquals(expected, next.read(source, directory));
                assertEquals(2, expected.blocks().size());
                assertEquals("yaml", expected.blocks().getFirst().language());
            }
        }
        assertEquals(List.of("conversion-cache-hit"), stages);
    }

    @Test
    void reusesConvertedContentWithoutInitialisingJruby() throws Exception {
        Path input = directory.resolve("post.adoc");
        var cache = new ConversionCache(input, directory.resolve("cache"), "implementation");
        String source = "= Title\n\nA *bold* {name} and `code`.\n\n|===\n|a |b\n|===\n";
        var stages = new ArrayList<String>();
        try (var first = new AsciiDocReader(cache, stages::add)) {
            var expected = first.read(source, directory, false, Map.of("name", "reader"));
            assertEquals(List.of("jruby-ready"), stages);
            stages.clear();
            try (var next = new AsciiDocReader(cache, stages::add)) {
                assertEquals(expected, next.read(source, directory, false, Map.of("name", "reader")));
            }
            assertEquals(List.of("conversion-cache-hit"), stages);
        }
    }

    @Test
    void validatesAllInputsAndKeepsOnlyTheLastVariantPerFile() throws Exception {
        var cache = new ConversionCache(directory.resolve("post"), directory.resolve("cache"), "one");
        String key = cache.key("source", directory, false, Map.of("a", "1", "b", "2"));
        cache.write(key, "<document/>");
        assertEquals("<document/>", cache.read(cache.key("source", directory, false, Map.of("b", "2", "a", "1"))));
        assertNull(cache.read(cache.key("changed", directory, false, Map.of("a", "1", "b", "2"))));
        assertNull(cache.read(cache.key("source", directory.resolve("other"), false, Map.of("a", "1", "b", "2"))));
        assertNull(cache.read(cache.key("source", directory, true, Map.of("a", "1", "b", "2"))));
        assertNull(cache.read(cache.key("source", directory, false, Map.of("a", "changed", "b", "2"))));
        var upgraded = new ConversionCache(directory.resolve("post"), directory.resolve("cache"), "two");
        assertNull(upgraded.read(upgraded.key("source", directory, false, Map.of("a", "1", "b", "2"))));
        cache.write("replacement", "<document>new</document>");
        assertNull(cache.read(key));
        try (var entries = Files.list(directory.resolve("cache"))) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void corruptXmlIsReconvertedAndExternalEntitiesRemainDisabled() throws Exception {
        var cache = new ConversionCache(directory.resolve("post"), directory.resolve("cache"), "one");
        Path secret = Files.writeString(directory.resolve("secret"), "SECRET_FILE_CONTENT");
        String source = "A safe paragraph.";
        String key = cache.key(source, directory, false, Map.of());
        cache.write(
                key,
                "<!DOCTYPE document [<!ENTITY external SYSTEM '" + secret.toUri() + "'>]><document>&external;</document>"
        );
        var stages = new ArrayList<String>();
        try (var reader = new AsciiDocReader(cache, stages::add)) {
            var result = reader.read(source, directory);
            assertTrue(result.blocks().toString().contains(source));
            assertFalse(result.blocks().toString().contains("SECRET_FILE_CONTENT"));
            assertEquals(List.of("jruby-ready"), stages);
        }
        assertNotNull(cache.read(key));
    }

    @Test
    void includesAndMissingIncludesAreNeverStored() throws Exception {
        var cache = new ConversionCache(directory.resolve("post"), directory.resolve("cache"), "one");
        String source = "include::part.adoc[]\n";
        try (var reader = new AsciiDocReader(cache, ignored -> {})) {
            reader.read(source, directory);
        }
        assertNull(cache.read(cache.key(source, directory, false, Map.of())));
        Files.writeString(directory.resolve("part.adoc"), "FIRST");
        try (var reader = new AsciiDocReader(cache, ignored -> {})) {
            assertTrue(reader.read(source, directory).blocks().toString().contains("FIRST"));
        }
        Files.writeString(directory.resolve("part.adoc"), "SECOND");
        try (var reader = new AsciiDocReader(cache, ignored -> {})) {
            assertTrue(reader.read(source, directory).blocks().toString().contains("SECOND"));
        }
        assertFalse(Files.exists(directory.resolve("cache")));
    }

    @Test
    void bypassesTimeSubstitutionsAndUnavailableStorage() throws Exception {
        var cache = new ConversionCache(directory.resolve("post"), directory.resolve("cache"), "one");
        assertNull(cache.key("{localdatetime}", directory, false, Map.of()));
        assertNull(cache.key("{name}", directory, false, Map.of("name", "{doctime}")));
        assertNull(cache.read(null));
        cache.write(null, "<document/>");
        cache.write("key", "x".repeat(8 * 1024 * 1024));
        assertFalse(Files.exists(directory.resolve("cache")));
        Path occupied = Files.writeString(directory.resolve("occupied"), "file");
        var unavailable = new ConversionCache(directory.resolve("post"), occupied, "one");
        unavailable.write("key", "<document/>");
        assertNull(unavailable.read("key"));
        assertEquals("file", Files.readString(occupied));
    }

    @Test
    void checksPayloadIntegrityAndRejectsSymbolicLinks() throws Exception {
        Path cacheDirectory = directory.resolve("cache");
        var cache = new ConversionCache(directory.resolve("post"), cacheDirectory, "one");
        String key = cache.key("source", directory, false, Map.of());
        cache.write(key, "<document/>");
        Path entry;
        try (var entries = Files.list(cacheDirectory)) {
            entry = entries.findFirst().orElseThrow();
        }
        if (entry.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(entry)
            );
        }
        Files.writeString(entry, Files.readString(entry).replace("<document/>", "<different/>"));
        assertNull(cache.read(key));
        Files.write(entry, new byte[8 * 1024 * 1024 + 1]);
        assertNull(cache.read(key));
        cache.write(key, "<document/>");
        Path moved = directory.resolve("moved");
        String original = Files.readString(entry);
        Files.move(entry, moved);
        Files.createSymbolicLink(entry, moved);
        assertNull(cache.read(key));
        assertEquals(original, Files.readString(moved));
    }
}

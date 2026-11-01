// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

class CacheFilesTest {
    @TempDir Path directory;

    @Test
    void fingerprintsReplaceOneSlotAndNeverResolveCallerPaths() throws Exception {
        var cache = new CacheFiles(directory);
        byte[] value = "value".getBytes(StandardCharsets.UTF_8);
        cache.write("../../outside", "first", value);
        assertArrayEquals(value, cache.read("../../outside", "first"));
        assertNull(cache.read("../../outside", "other"));
        cache.write("../../outside", "other", value);
        assertNull(cache.read("../../outside", "first"));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
        assertFalse(Files.exists(directory.getParent().resolve("outside")));
        assertFalse(CacheFiles.key(List.of("ab", "c")).equals(CacheFiles.key(List.of("a", "bc"))));
    }

    @Test
    void checksIntegrityBoundsPermissionsAndLinks() throws Exception {
        var cache = new CacheFiles(directory);
        byte[] value = "private content".getBytes(StandardCharsets.UTF_8);
        cache.write("slot", "version", value);
        Path entry;
        try (var entries = Files.list(directory)) {
            entry = entries.findFirst().orElseThrow();
        }
        if (entry.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(entry));
        }
        Files.writeString(entry, Files.readString(entry).replace("private", "changed"));
        assertNull(cache.read("slot", "version"));
        Files.write(entry, new byte[8 * 1024 * 1024 + 1]);
        assertNull(cache.read("slot", "version"));
        cache.write("slot", "version", value);
        Path moved = directory.resolve("moved");
        Files.move(entry, moved);
        Files.createSymbolicLink(entry, moved);
        assertNull(cache.read("slot", "version"));
    }

    @Test
    void prunesOldEntriesOnceBeforeWritingAndIgnoresOtherFiles() throws Exception {
        var initial = new CacheFiles(directory);
        initial.write("old", "v", new byte[10]);
        Path old;
        try (var entries = Files.list(directory)) {
            old = entries.findFirst().orElseThrow();
        }
        Files.setLastModifiedTime(old, FileTime.fromMillis(1));
        initial.write("recent", "v", new byte[10]);
        Path unrelated = Files.writeString(directory.resolve("leave.txt"), "leave");
        var bounded = new CacheFiles(directory, 150);
        bounded.write("new", "v", new byte[10]);
        assertNull(bounded.read("old", "v"));
        assertArrayEquals(new byte[10], bounded.read("recent", "v"));
        assertArrayEquals(new byte[10], bounded.read("new", "v"));
        assertEquals("leave", Files.readString(unrelated));
    }

    @Test
    void unavailableAndOversizedEntriesLeaveStorageUntouched() throws Exception {
        Path occupied = Files.writeString(directory.resolve("occupied"), "file");
        var cache = new CacheFiles(occupied);
        cache.write("slot", "v", new byte[1]);
        assertNull(cache.read("slot", "v"));
        Path unused = directory.resolve("unused");
        new CacheFiles(unused).write("slot", "v", new byte[8 * 1024 * 1024]);
        assertFalse(Files.exists(unused));
        assertNull(cache.read("slot", null));
        assertNull(CacheFiles.artifactIdentity(String.class));
    }
}

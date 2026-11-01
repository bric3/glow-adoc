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
    void acceptsLauncherHashesOnlyForTheMatchingArtifactAndRestoresFallback() throws Exception {
        Path artifact = Files.writeString(directory.resolve("app.jar"), "packaged bytes");
        Path other = Files.writeString(directory.resolve("other.jar"), "other bytes");
        String pathProperty = "glowadoc.launcher.artifact";
        String hashProperty = "glowadoc.launcher.sha256";
        String previousPath = System.getProperty(pathProperty);
        String previousHash = System.getProperty(hashProperty);
        try {
            System.clearProperty(pathProperty);
            System.clearProperty(hashProperty);
            assertNull(CacheFiles.launcherIdentity(artifact));
            System.setProperty(pathProperty, artifact.toString());
            assertNull(CacheFiles.launcherIdentity(artifact));
            System.setProperty(hashProperty, "AB".repeat(32));
            assertEquals("ab".repeat(32), CacheFiles.launcherIdentity(artifact));
            Path alias = Files.createSymbolicLink(directory.resolve("alias.jar"), artifact);
            System.setProperty(pathProperty, alias.toString());
            assertEquals("ab".repeat(32), CacheFiles.launcherIdentity(artifact));
            assertNull(CacheFiles.launcherIdentity(other));
            for (String malformed : List.of("", "a".repeat(63), "a".repeat(65), "g".repeat(64))) {
                System.setProperty(hashProperty, malformed);
                assertNull(CacheFiles.launcherIdentity(artifact));
            }
            System.setProperty(hashProperty, "ab".repeat(32));
            System.setProperty(pathProperty, "bad\u0000path");
            assertNull(CacheFiles.launcherIdentity(artifact));
            System.setProperty(pathProperty, directory.resolve("missing").toString());
            assertNull(CacheFiles.launcherIdentity(artifact));
        } finally {
            if (previousPath == null) {
                System.clearProperty(pathProperty);
            } else {
                System.setProperty(pathProperty, previousPath);
            }
            if (previousHash == null) {
                System.clearProperty(hashProperty);
            } else {
                System.setProperty(hashProperty, previousHash);
            }
        }
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

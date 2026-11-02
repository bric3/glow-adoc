// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.cache;

import java.io.IOException;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Private, bounded cache entries. Unavailable storage behaves like an empty cache.
 */
public final class CacheFiles {
    private static final int LIMIT = 8 * 1024 * 1024;
    private static final int HASH_LINE_LENGTH = 64 + 1;
    private final Path directory;
    private final long budget;
    private boolean swept;

    public CacheFiles(Path directory) {
        this(directory, 128L * 1024 * 1024);
    }

    CacheFiles(Path directory, long budget) {
        this.directory = directory;
        this.budget = budget;
    }

    /**
     * Only a shared packaged artifact supplies a complete identity for these components.
     */
    public static String artifactIdentity(Class<?>... components) {
        URL own = origin(CacheFiles.class);
        if (own == null) {
            return null;
        }
        for (Class<?> component : components) {
            if (component.getClassLoader() != CacheFiles.class.getClassLoader() || !own.equals(origin(component))) {
                return null;
            }
        }
        return Artifact.IDENTITY;
    }

    private static final class Artifact {
        private static final String IDENTITY = identity();

        private static String identity() {
            try {
                Path artifact = Path.of(origin(CacheFiles.class).toURI());
                if (!Files.isRegularFile(artifact)) {
                    return null;
                }
                String supplied = launcherIdentity(artifact);
                if (supplied != null) {
                    return supplied;
                }
                var digest = digest();
                try (var stream = new DigestInputStream(Files.newInputStream(artifact), digest)) {
                    stream.transferTo(OutputStream.nullOutputStream());
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (IOException | URISyntaxException | SecurityException e) {
                return null;
            }
        }
    }

    /**
     * Reuse the launcher's full hash only for the artifact supplying these classes.
     */
    static String launcherIdentity(Path artifact) {
        try {
            String path = System.getProperty("glowadoc.launcher.artifact");
            String hash = System.getProperty("glowadoc.launcher.sha256");
            if (path == null || hash == null || hash.length() != 64) {
                return null;
            }
            for (int i = 0; i < hash.length(); i++) {
                if (!HexFormat.isHexDigit(hash.charAt(i))) {
                    return null;
                }
            }
            return Files.isSameFile(artifact, Path.of(path)) ? hash.toLowerCase(Locale.ROOT) : null;
        } catch (IOException | InvalidPathException | SecurityException e) {
            return null;
        }
    }

    private static URL origin(Class<?> type) {
        // Archived CodeSource URLs can retain the training path after AOT relocation.
        try {
            URL resource = type.getResource("/" + type.getName().replace('.', '/') + ".class");
            if (resource != null && resource.openConnection() instanceof JarURLConnection jar) {
                URL location = jar.getJarFileURL();
                return location.getProtocol().equals("file") ? location : null;
            }
        } catch (IOException | SecurityException ignored) {
            // Unavailable or unpackaged classes leave disk caches disabled.
        }
        return null;
    }

    public static String key(List<String> values) {
        var digest = digest();
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public byte[] read(String slot, String fingerprint) {
        if (fingerprint == null) {
            return null;
        }
        try (var stream = Files.newInputStream(entry(slot), LinkOption.NOFOLLOW_LINKS)) {
            int start = 2 * HASH_LINE_LENGTH;
            byte[] prefix = stream.readNBytes(start);
            if (prefix.length != start) {
                return null;
            }
            String header = new String(prefix, StandardCharsets.US_ASCII);
            if (!header.startsWith(hash(fingerprint.getBytes(StandardCharsets.UTF_8)) + "\n")) {
                return null;
            }
            byte[] value = stream.readNBytes(LIMIT - start + 1);
            if (value.length > LIMIT - start) {
                return null;
            }
            return header.substring(HASH_LINE_LENGTH).equals(hash(value) + "\n") ? value : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    public void write(String slot, String fingerprint, byte[] value) {
        if (fingerprint == null || value.length > LIMIT - 2 * HASH_LINE_LENGTH) {
            return;
        }
        Path temporary = null;
        try {
            boolean posix = directory.getFileSystem().supportedFileAttributeViews().contains("posix");
            Files.createDirectories(
                    directory,
                    posix
                    ? new FileAttribute<?>[] {
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
                    }
                    : new FileAttribute<?>[0]
            );
            if (!swept) {
                swept = true;
                prune();
            }
            temporary = Files.createTempFile(directory, ".cache-", ".tmp");
            try (var out = Files.newOutputStream(temporary)) {
                out.write((hash(fingerprint.getBytes(StandardCharsets.UTF_8)) + "\n" + hash(value) + "\n")
                    .getBytes(StandardCharsets.US_ASCII)
                );
                out.write(value);
            }
            try {
                Files.move(temporary, entry(slot), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, entry(slot), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException ignored) {
            // Cache writes never prevent rendering.
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                }
            }
        }
    }

    private Path entry(String slot) {
        return directory.resolve(hash(slot.getBytes(StandardCharsets.UTF_8)) + ".cache");
    }

    private void prune() throws IOException {
        record Entry(Path path, long bytes, long modified) {}
        var entries = new ArrayList<Entry>();
        long total = 0;
        try (var paths = Files.newDirectoryStream(directory, "*.cache")) {
            for (Path path : paths) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                long bytes = Files.size(path);
                total += bytes;
                entries.add(new Entry(path, bytes, Files.getLastModifiedTime(path).toMillis()));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::modified));
        for (Entry entry : entries) {
            if (total <= budget) {
                break;
            }
            Files.deleteIfExists(entry.path());
            total -= entry.bytes();
        }
    }

    private static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}

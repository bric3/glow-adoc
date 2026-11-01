// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.asciidoctor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** One private, replaceable conversion per input file. Cache failures leave parsing available. */
final class ConversionCache {
    private static final int LIMIT = 8 * 1024 * 1024;
    private static final int HASH_LINE_LENGTH = 64 + 1;
    private final Path entry;
    private final String implementation;

    ConversionCache(Path input, Path directory, String implementation) {
        this.entry =
                directory.resolve(hash(input.toAbsolutePath().normalize().toString()) + ".xml");
        this.implementation = implementation;
    }

    static ConversionCache open(Path input, Path directory) {
        if (input == null || directory == null || !Files.isRegularFile(input)) {
            return null;
        }
        try {
            var origin = AsciiDocReader.class.getProtectionDomain().getCodeSource();
            if (origin == null) {
                return null;
            }
            Path artifact = Path.of(origin.getLocation().toURI());
            // Exploded development classpaths do not have one complete implementation identity.
            if (!Files.isRegularFile(artifact)) {
                return null;
            }
            var digest = digest();
            try (var stream = new DigestInputStream(Files.newInputStream(artifact), digest)) {
                stream.transferTo(OutputStream.nullOutputStream());
            }
            return new ConversionCache(input, directory, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | URISyntaxException | SecurityException e) {
            return null;
        }
    }

    String key(String source, Path base, boolean showShortcodes, Map<String, String> attributes) {
        // Time-dependent substitutions must be evaluated by Asciidoctor on every read.
        if (temporal(source) || attributes.values().stream().anyMatch(ConversionCache::temporal)) {
            return null;
        }
        var digest = digest();
        for (String value :
                List.of(
                        implementation,
                        Runtime.version().toString(),
                        System.getProperty("user.home"),
                        Locale.getDefault().toLanguageTag(),
                        ZoneId.systemDefault().getId(),
                        base.toString(),
                        Boolean.toString(showShortcodes),
                        source)) {
            update(digest, value);
        }
        for (String name : List.of("TZ", "LANG", "LC_ALL", "LC_TIME", "SOURCE_DATE_EPOCH")) {
            update(digest, System.getenv().getOrDefault(name, ""));
        }
        new TreeMap<>(attributes)
                .forEach(
                        (name, value) -> {
                            update(digest, name);
                            update(digest, value);
                        });
        return HexFormat.of().formatHex(digest.digest());
    }

    String read(String key) {
        if (key == null) {
            return null;
        }
        try (var stream = Files.newInputStream(entry, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) {
                return null;
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (!content.startsWith(key + "\n") || content.length() < 2 * HASH_LINE_LENGTH) {
                return null;
            }
            String xml = content.substring(2 * HASH_LINE_LENGTH);
            return content.substring(HASH_LINE_LENGTH, 2 * HASH_LINE_LENGTH)
                            .equals(hash(xml) + "\n")
                    ? xml
                    : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    void write(String key, String xml) {
        if (key == null) {
            return;
        }
        byte[] content = (key + "\n" + hash(xml) + "\n" + xml).getBytes(StandardCharsets.UTF_8);
        if (content.length > LIMIT) {
            return;
        }
        Path temporary = null;
        try {
            boolean posix = entry.getFileSystem().supportedFileAttributeViews().contains("posix");
            Files.createDirectories(
                    entry.getParent(),
                    posix
                            ? new FileAttribute<?>[] {
                                PosixFilePermissions.asFileAttribute(
                                        PosixFilePermissions.fromString("rwx------"))
                            }
                            : new FileAttribute<?>[0]);
            temporary = Files.createTempFile(entry.getParent(), ".conversion-", ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(
                        temporary,
                        entry,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException ignored) {
            // Read-only and unavailable caches must not prevent reading a document.
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                    // The next read remains independent of temporary files.
                }
            }
        }
    }

    private static boolean temporal(String text) {
        return List.of("localdate", "localtime", "localyear", "docdate", "doctime", "docyear")
                .stream()
                .anyMatch(text::contains);
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hash(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}

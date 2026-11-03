// SPDX-License-Identifier: MPL-2.0
//JAVA 27
//DEPS com.google.code.gson:gson:2.15.0
package io.github.bric3.glowadoc.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Refreshes the newest draft with tested artifacts and updates published releases.
 */
public class Release {
    private final String repository;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    Release(String repository, String token) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid repository");
        }
        this.repository = repository;
        this.token = token;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            selfTest();
            return;
        }
        var publisher = new Release(System.getenv("GITHUB_REPOSITORY"), System.getenv("GH_TOKEN"));
        if (args.length == 1 && args[0].equals("select")) {
            JsonObject draft = publisher.selectDraft();
            if (draft != null) {
                String tag = draft.get("tag_name").getAsString();
                if (!tag.matches("v[0-9][0-9A-Za-z.-]*")) {
                    throw new IllegalArgumentException("Invalid draft tag: " + tag);
                }
                Files.writeString(
                        Path.of(System.getenv("GITHUB_OUTPUT")),
                        String.join(
                                "\n",
                                "id=" + draft.get("id").getAsLong(),
                                "tag=" + tag,
                                "prerelease=" + draft.get("prerelease").getAsBoolean(),
                                ""
                        ),
                        StandardOpenOption.APPEND
                );
            }
            return;
        }
        if (args.length != 2 || !List.of("draft", "final").contains(args[0])) {
            throw new IllegalArgumentException("Usage: Release.java select | draft|final ARTIFACT_DIRECTORY");
        }
        var metadata = new Properties();
        Path directory = Path.of(args[1]);
        try (var input = Files.newInputStream(directory.resolve("version.properties"))) {
            metadata.load(input);
        }
        String version = metadata.getProperty("version");
        boolean draft = args[0].equals("draft");
        if (version == null
                || !version.matches("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")
                || version.endsWith("-SNAPSHOT")) {
            throw new IllegalArgumentException("Unexpected " + args[0] + " version: " + version);
        }
        String tag = "v" + version;
        if (!tag.equals(System.getenv("RELEASE_TAG"))) {
            throw new IllegalArgumentException("Release tag does not match artifact version");
        }
        var files = new ArrayList<Path>();
        for (String suffix : List.of("", "-without-aot")) {
            Path distribution = directory.resolve("glow-adoc-" + version + suffix + ".zip");
            validateDistribution(distribution, version, suffix.isEmpty());
            files.add(distribution);
        }
        var checksums = new StringBuilder();
        for (Path file : files) {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] bytes = new byte[65536];
                int count;
                while ((count = input.read(bytes)) >= 0) {
                    digest.update(bytes, 0, count);
                }
            }
            checksums.append(HexFormat.of().formatHex(digest.digest())).append("  ").append(file.getFileName()).append(
                    '\n'
            );
        }
        Path checksumFile = directory.resolve("checksums-" + version + ".txt");
        Files.writeString(checksumFile, checksums);
        files.add(checksumFile);
        long id = draft
                ? Long.parseLong(System.getenv("RELEASE_ID"))
                : publisher.request("GET", "releases/tags/" + tag, null).getAsJsonObject().get("id").getAsLong();
        publisher.update(draft, id, tag, System.getenv("GITHUB_SHA"), files);
    }

    static void validateDistribution(Path distribution, String version, boolean aot) throws Exception {
        try (var archive = new ZipFile(distribution.toFile())) {
            String root = "glow-adoc-" + version + "/";
            var launcher = archive.getEntry(root + "bin/glow-adoc");
            var jar = archive.getEntry(root + "lib/glow-adoc.jar");
            if (launcher == null || launcher.isDirectory() || jar == null || jar.isDirectory()) {
                throw new IllegalStateException("Missing launcher or jar: " + distribution);
            }
            if (aot != (archive.getEntry(root + "lib/aot/cache.aot") != null)
                    || aot != (archive.getEntry(root + "lib/aot/cache.key") != null)) {
                throw new IllegalStateException("AOT contents mismatch: " + distribution);
            }
            Path staged = Files.createTempFile("glow-adoc-release-", ".jar");
            try {
                try (var input = archive.getInputStream(jar)) {
                    Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
                }
                try (var input = new JarFile(staged.toFile())) {
                    var manifest = input.getManifest();
                    if (manifest == null
                            || !version.equals(manifest.getMainAttributes().getValue("Implementation-Version"))) {
                        throw new IllegalStateException("Jar version mismatch: " + distribution);
                    }
                }
            } finally {
                Files.deleteIfExists(staged);
            }
        }
    }

    JsonObject selectDraft() throws Exception {
        JsonObject selected = null;
        for (int page = 1; ; page++) {
            var batch = request("GET", "releases?per_page=100&page=" + page, null).getAsJsonArray();
            for (var item : batch) {
                var candidate = item.getAsJsonObject();
                String created = candidate.get("created_at").getAsString();
                if (candidate.get("draft").getAsBoolean()
                        && (selected == null || created.compareTo(selected.get("created_at").getAsString()) > 0)) {
                    selected = candidate;
                }
            }
            if (batch.size() < 100) {
                break;
            }
        }
        if (selected == null) {
            System.out.println("No draft release to refresh");
        }
        return selected;
    }

    void update(boolean draft, long id, String tag, String sha, List<Path> files) throws Exception {
        var release = request("GET", "releases/" + id, null).getAsJsonObject();
        if (!release.get("tag_name").getAsString().equals(tag)) {
            throw new IllegalStateException("Release tag changed during the build");
        }
        if (draft && !release.get("draft").getAsBoolean()) {
            System.out.println("Release is already public; keeping final assets");
            return;
        }
        if (!draft && release.get("draft").getAsBoolean()) {
            throw new IllegalStateException("Expected a published release for " + tag);
        }
        if (draft) {
            var body = new JsonObject();
            // Preserve the intended tag while its Git ref has not been created.
            body.addProperty("tag_name", tag);
            body.addProperty("target_commitish", sha);
            request("PATCH", "releases/" + id, body);
        }
        for (Path file : files) {
            var current = request("GET", "releases/" + id, null).getAsJsonObject();
            if (draft && !current.get("draft").getAsBoolean()) {
                return;
            }
            for (var asset : assets(id)) {
                if (asset.getAsJsonObject().get("name").getAsString().equals(file.getFileName().toString())) {
                    request("DELETE", "releases/assets/" + asset.getAsJsonObject().get("id").getAsLong(), null);
                }
            }
            upload(id, file);
        }
        var current = request("GET", "releases/" + id, null).getAsJsonObject();
        if (draft && !current.get("draft").getAsBoolean()) {
            return;
        }
        // Keep previous downloads until every new upload succeeds.
        var expected = files
            .stream()
            .map(file -> file.getFileName().toString())
            .toList();
        for (var asset : assets(id)) {
            String name = asset.getAsJsonObject().get("name").getAsString();
            if (!expected.contains(name)
                    && (name.startsWith("glow-adoc-") && (name.endsWith(".jar") || name.endsWith(".zip"))
                    || name.startsWith("checksums-") && name.endsWith(".txt"))) {
                request("DELETE", "releases/assets/" + asset.getAsJsonObject().get("id").getAsLong(), null);
            }
        }
        current = request("GET", "releases/" + id, null).getAsJsonObject();
        var uploaded = new ArrayList<String>();
        for (var asset : assets(id)) {
            uploaded.add(asset.getAsJsonObject().get("name").getAsString());
        }
        if (current.get("draft").getAsBoolean() != draft
                || !tag.equals(current.get("tag_name").getAsString())
                || draft && !sha.equals(current.get("target_commitish").getAsString())
                || !uploaded.containsAll(expected)) {
            throw new IllegalStateException("Release target or artifact uploads were not updated");
        }
    }

    private JsonArray assets(long id) throws Exception {
        var result = new JsonArray();
        for (int page = 1; ; page++) {
            var batch = request("GET", "releases/" + id + "/assets?per_page=100&page=" + page, null).getAsJsonArray();
            result.addAll(batch);
            if (batch.size() < 100) {
                return result;
            }
        }
    }

    JsonElement request(String method, String path, JsonObject body) throws Exception {
        String response = send(builder(URI.create("https://api.github.com/repos/" + repository + "/" + path))
            .header("Content-Type", "application/json")
            .method(
                    method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()
                    )
            )
            .build()
        );
        return response.isBlank() ? new JsonObject() : JsonParser.parseString(response);
    }

    void upload(long id, Path file) throws Exception {
        send(builder(URI.create(
                "https://uploads.github.com/repos/"
                + repository
                + "/releases/"
                + id
                + "/assets?name="
                + URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
        ))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofFile(file))
            .build()
        );
    }

    private HttpRequest.Builder builder(URI uri) {
        return HttpRequest
            .newBuilder(uri)
            .timeout(Duration.ofMinutes(5))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private String send(HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "GitHub " + request.method() + " " + request.uri().getPath() + ": HTTP " + response.statusCode()
            );
        }
        return response.body();
    }

    static void selfTest() throws Exception {
        Path distribution = Files.createTempFile("glow-adoc-release-test-", ".zip");
        try {
            for (String scenario :
                    List.of("missing launcher", "wrong version", "missing aot", "unexpected aot", "valid", "valid aot")) {
                try (var archive = new ZipOutputStream(Files.newOutputStream(distribution))) {
                    if (!scenario.equals("missing launcher")) {
                        archive.putNextEntry(new ZipEntry("glow-adoc-0.1.0/bin/glow-adoc"));
                        archive.write("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
                        archive.closeEntry();
                    }
                    archive.putNextEntry(new ZipEntry("glow-adoc-0.1.0/lib/glow-adoc.jar"));
                    var manifest = new Manifest();
                    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                    manifest.getMainAttributes().putValue(
                            "Implementation-Version",
                            scenario.equals("wrong version") ? "0.2.0" : "0.1.0"
                    );
                    var jar = new ByteArrayOutputStream();
                    try (var output = new JarOutputStream(jar, manifest)) {
                        output.finish();
                    }
                    archive.write("#!/bin/sh\nexit 0\n\n".getBytes(StandardCharsets.UTF_8));
                    archive.write(jar.toByteArray());
                    archive.closeEntry();
                    if (scenario.equals("valid aot") || scenario.equals("unexpected aot")) {
                        for (String name : List.of("cache.aot", "cache.key")) {
                            archive.putNextEntry(new ZipEntry("glow-adoc-0.1.0/lib/aot/" + name));
                            archive.write("cache".getBytes(StandardCharsets.UTF_8));
                            archive.closeEntry();
                        }
                    }
                }
                try {
                    validateDistribution(
                            distribution,
                            "0.1.0",
                            scenario.equals("valid aot") || scenario.equals("missing aot")
                    );
                    if (!scenario.startsWith("valid")) {
                        throw new AssertionError("Accepted distribution with " + scenario);
                    }
                } catch (IllegalStateException expected) {
                    if (scenario.startsWith("valid")
                            || !expected
                        .getMessage()
                        .contains(
                                switch (scenario) {
                                    case "wrong version" -> "version mismatch";
                                    case "missing aot", "unexpected aot" -> "AOT contents mismatch";
                                    default -> "Missing launcher";
                                }
                        )) {
                        throw expected;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(distribution);
        }
        var fake = new Fake();
        if (fake.selectDraft() != null) {
            throw new AssertionError("Selected a release when no draft exists");
        }
        fake.exists = true;
        for (int i = 0; i < 100; i++) {
            fake.otherReleases.add(JsonParser.parseString("{id:2,draft:false,created_at:'2026-11-03T00:00:00Z'}"));
        }
        fake.otherReleases.add(JsonParser.parseString("{id:3,draft:true,created_at:'2026-11-01T00:00:00Z'}"));
        if (fake.selectDraft().get("id").getAsLong() != 1) {
            throw new AssertionError("Selected the wrong draft");
        }
        var files = List.of(
                Path.of("glow-adoc-0.2.0-rc.1.zip"),
                Path.of("glow-adoc-0.2.0-rc.1-without-aot.zip"),
                Path.of("checksums-0.2.0-rc.1.txt")
        );
        new Fake().update(true, 1, "v0.2.0-rc.1", "head", files);
        fake.names.addAll(List.of(
                "glow-adoc-0.1.0-SNAPSHOT.jar",
                "glow-adoc-0.1.0.zip",
                "checksums-0.1.0-SNAPSHOT.txt",
                "notes.pdf"
        ));
        fake.fail = true;
        try {
            fake.update(true, 1, "v0.2.0-rc.1", "head", files);
            throw new AssertionError("Upload should fail");
        } catch (java.io.IOException expected) {
            if (!fake.names.containsAll(List.of("glow-adoc-0.1.0-SNAPSHOT.jar", "glow-adoc-0.1.0.zip"))) {
                throw new AssertionError("Lost previous artifacts on failure");
            }
        }
        fake.fail = false;
        fake.update(true, 1, "v0.2.0-rc.1", "head", files);
        fake.update(true, 1, "v0.2.0-rc.1", "head", files);
        if (!fake.body.equals("human notes")
                || !fake.name.equals("Human title")
                || !fake.prerelease
                || !fake.target.equals("head")
                || fake.names.size() != 4
                || !fake.names.contains("notes.pdf")) {
            throw new AssertionError("Lost draft metadata or left incorrect assets");
        }
        fake.ignoreUploads = true;
        try {
            fake.update(true, 1, "v0.2.0-rc.1", "head", files);
            throw new AssertionError("Missing uploads should fail verification");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("artifact uploads")) {
                throw expected;
            }
        }
        fake.ignoreUploads = false;
        fake.draft = false;
        fake.update(true, 1, "v0.2.0-rc.1", "head", files);
        fake.update(false, 1, "v0.2.0-rc.1", "head", files);
        fake.update(false, 1, "v0.2.0-rc.1", "head", files);
        if (!fake.names.containsAll(files
            .stream()
            .map(file -> file.getFileName().toString())
            .toList())) {
            throw new AssertionError("Retry left missing assets");
        }
        try {
            fake.update(false, 1, "v0.3.0", "head", files);
            throw new AssertionError("A changed tag should fail");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("tag changed")) {
                throw expected;
            }
        }
        System.out.println("Release lifecycle checks passed");
    }

    private static final class Fake extends Release {
        boolean exists;
        boolean draft = true;
        boolean fail;
        boolean ignoreUploads;
        boolean prerelease = true;
        String body = "human notes";
        String name = "Human title";
        String tag = "v0.2.0-rc.1";
        String target = "old";
        final List<String> names = new ArrayList<>();
        final JsonArray otherReleases = new JsonArray();

        Fake() {
            super("example/glow-adoc", "unused");
        }

        @Override
        JsonElement request(String method, String path, JsonObject payload) {
            var release = JsonParser.parseString("{id:1,created_at:'2026-11-02T00:00:00Z'}").getAsJsonObject();
            release.addProperty("tag_name", tag);
            release.addProperty("draft", draft);
            release.addProperty("body", body);
            release.addProperty("name", name);
            release.addProperty("prerelease", prerelease);
            release.addProperty("target_commitish", target);
            if (path.startsWith("releases?")) {
                var all = new JsonArray();
                all.addAll(otherReleases);
                if (exists) {
                    all.add(release);
                }
                var result = new JsonArray();
                int start = (Integer.parseInt(path.substring(path.lastIndexOf('=') + 1)) - 1) * 100;
                for (int i = start; i < Math.min(start + 100, all.size()); i++) {
                    result.add(all.get(i));
                }
                return result;
            }
            if (path.contains("/assets?")) {
                var result = new JsonArray();
                for (int i = 0; i < names.size(); i++) {
                    var asset = new JsonObject();
                    asset.addProperty("id", names.get(i).hashCode());
                    asset.addProperty("name", names.get(i));
                    result.add(asset);
                }
                return result;
            }
            if (method.equals("DELETE")) {
                int assetId = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                names.removeIf(name -> name.hashCode() == assetId);
            }
            if (method.equals("POST")) {
                throw new AssertionError("Release creation is not allowed");
            }
            if (payload != null && payload.has("body")) {
                body = payload.get("body").getAsString();
            }
            if (payload != null && payload.has("name")) {
                name = payload.get("name").getAsString();
            }
            if (payload != null && payload.has("prerelease")) {
                prerelease = payload.get("prerelease").getAsBoolean();
            }
            if (payload != null && payload.has("target_commitish")) {
                // GitHub replaces an omitted draft tag with a temporary name when updating the target.
                tag = payload.has("tag_name") ? payload.get("tag_name").getAsString() : "untagged-draft";
                target = payload.get("target_commitish").getAsString();
            }
            return release;
        }

        @Override
        void upload(long id, Path file) throws Exception {
            if (fail) {
                throw new java.io.IOException("Simulated failed upload");
            }
            if (!ignoreUploads) {
                names.add(file.getFileName().toString());
            }
        }
    }
}

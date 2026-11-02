// SPDX-License-Identifier: MPL-2.0
//JAVA 27
//DEPS com.google.code.gson:gson:2.13.2
package io.github.bric3.glowadoc.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Maintains the master draft and replaces snapshot assets after publication.
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
        if (args.length != 2 || !List.of("snapshot", "final").contains(args[0])) {
            throw new IllegalArgumentException("Usage: Release.java snapshot|final ARTIFACT_DIRECTORY");
        }
        var metadata = new Properties();
        Path directory = Path.of(args[1]);
        try (var input = Files.newInputStream(directory.resolve("version.properties"))) {
            metadata.load(input);
        }
        String version = metadata.getProperty("version");
        boolean snapshot = args[0].equals("snapshot");
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+" + (snapshot ? "-SNAPSHOT" : ""))) {
            throw new IllegalArgumentException("Unexpected " + args[0] + " version: " + version);
        }
        String tag = "v" + version.replace("-SNAPSHOT", "");
        if (!snapshot && !tag.equals(System.getenv("RELEASE_TAG"))) {
            throw new IllegalArgumentException("Release tag does not match artifact version");
        }
        var files = new ArrayList<>(List.of(
                directory.resolve("glow-adoc-" + version + ".jar"),
                directory.resolve("glow-adoc-" + version + "-app.jar")
        ));
        var checksums = new StringBuilder();
        for (Path jar : files) {
            try (var archive = new JarFile(jar.toFile())) {
                if (!version.equals(archive.getManifest().getMainAttributes().getValue("Implementation-Version"))) {
                    throw new IllegalStateException("Jar version mismatch: " + jar);
                }
            }
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(jar)) {
                byte[] bytes = new byte[65536];
                int count;
                while ((count = input.read(bytes)) >= 0) {
                    digest.update(bytes, 0, count);
                }
            }
            checksums.append(HexFormat.of().formatHex(digest.digest())).append("  ").append(jar.getFileName()).append(
                    '\n'
            );
        }
        Path checksumFile = directory.resolve("checksums-" + version + ".txt");
        Files.writeString(checksumFile, checksums);
        files.add(checksumFile);
        new Release(System.getenv("GITHUB_REPOSITORY"), System.getenv("GH_TOKEN"))
            .update(
                    snapshot,
                    tag,
                    System.getenv("GITHUB_SHA"),
                    files,
                    Files.readString(Path.of("docs/RELEASE-NOTES.md"))
            );
    }

    void update(boolean snapshot, String tag, String sha, List<Path> files, String notes) throws Exception {
        if (snapshot
                && !sha.equals(request("GET", "commits/master", null).getAsJsonObject().get("sha").getAsString())) {
            System.out.println("Skipping superseded master build");
            return;
        }
        JsonObject release = null;
        for (int page = 1; release == null; page++) {
            var batch = request("GET", "releases?per_page=100&page=" + page, null).getAsJsonArray();
            for (var candidate : batch) {
                if (candidate.getAsJsonObject().get("tag_name").getAsString().equals(tag)) {
                    release = candidate.getAsJsonObject();
                }
            }
            if (batch.size() < 100) {
                break;
            }
        }
        if (snapshot && release != null && !release.get("draft").getAsBoolean()) {
            System.out.println("Release is already public; keeping final assets");
            return;
        }
        if (!snapshot && (release == null || release.get("draft").getAsBoolean())) {
            throw new IllegalStateException("Expected a published release for " + tag);
        }
        if (snapshot) {
            var body = new JsonObject();
            body.addProperty("tag_name", tag);
            body.addProperty("target_commitish", sha);
            if (release == null) {
                body.addProperty("draft", true);
                body.addProperty("name", tag);
                body.addProperty("body", notes);
                release = request("POST", "releases", body).getAsJsonObject();
            } else {
                release = request("PATCH", "releases/" + release.get("id").getAsLong(), body).getAsJsonObject();
            }
        }
        long id = release.get("id").getAsLong();
        for (Path file : files) {
            var current = request("GET", "releases/" + id, null).getAsJsonObject();
            if (snapshot && !current.get("draft").getAsBoolean()) {
                return;
            }
            for (var asset : assets(id)) {
                if (asset.getAsJsonObject().get("name").getAsString().equals(file.getFileName().toString())) {
                    request("DELETE", "releases/assets/" + asset.getAsJsonObject().get("id").getAsLong(), null);
                }
            }
            upload(id, file);
        }
        if (!snapshot) {
            // Snapshot downloads remain available until every final upload succeeds.
            String version = tag.substring(1);
            var obsolete = List.of(
                    "glow-adoc-" + version + "-SNAPSHOT.jar",
                    "glow-adoc-" + version + "-SNAPSHOT-app.jar",
                    "checksums-" + version + "-SNAPSHOT.txt"
            );
            for (var asset : assets(id)) {
                if (obsolete.contains(asset.getAsJsonObject().get("name").getAsString())) {
                    request("DELETE", "releases/assets/" + asset.getAsJsonObject().get("id").getAsLong(), null);
                }
            }
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
        var fake = new Fake();
        var snapshot = List.of(
                Path.of("glow-adoc-0.1.0-SNAPSHOT.jar"),
                Path.of("glow-adoc-0.1.0-SNAPSHOT-app.jar"),
                Path.of("checksums-0.1.0-SNAPSHOT.txt")
        );
        var finals =
                List.of(
                        Path.of("glow-adoc-0.1.0.jar"),
                        Path.of("glow-adoc-0.1.0-app.jar"),
                        Path.of("checksums-0.1.0.txt")
        );
        fake.update(true, "v0.1.0", "head", snapshot, "notes");
        if (!fake.draft || !fake.names.contains("glow-adoc-0.1.0-SNAPSHOT.jar")) {
            throw new AssertionError();
        }
        fake.body = "human notes";
        fake.update(true, "v0.1.0", "head", snapshot, "replacement notes");
        if (!fake.body.equals("human notes")) {
            throw new AssertionError("Lost notes");
        }
        fake.draft = false;
        fake.update(true, "v0.1.0", "head", snapshot, "notes");
        fake.fail = true;
        try {
            fake.update(false, "v0.1.0", "head", finals, "notes");
            throw new AssertionError("Upload should fail");
        } catch (java.io.IOException expected) {
            if (!fake.names.contains("glow-adoc-0.1.0-SNAPSHOT.jar")) {
                throw new AssertionError("Lost snapshot on failure");
            }
        }
        fake.fail = false;
        fake.update(false, "v0.1.0", "head", finals, "notes");
        fake.update(false, "v0.1.0", "head", finals, "notes");
        if (!fake.names.equals(finals
            .stream()
            .map(file -> file.getFileName().toString())
            .toList())) {
            throw new AssertionError("Retry left incorrect assets");
        }
        System.out.println("Release lifecycle checks passed");
    }

    private static final class Fake extends Release {
        boolean exists;
        boolean draft = true;
        boolean fail;
        String body = "notes";
        final List<String> names = new ArrayList<>();

        Fake() {
            super("example/glow-adoc", "unused");
        }

        @Override
        JsonElement request(String method, String path, JsonObject payload) {
            var release = JsonParser.parseString("{id:1,tag_name:'v0.1.0'}").getAsJsonObject();
            release.addProperty("draft", draft);
            release.addProperty("body", body);
            if (path.equals("commits/master")) {
                return JsonParser.parseString("{sha:'head'}");
            }
            if (path.startsWith("releases?")) {
                var result = new JsonArray();
                if (exists) {
                    result.add(release);
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
                exists = true;
            }
            if (payload != null && payload.has("body")) {
                body = payload.get("body").getAsString();
            }
            return release;
        }

        @Override
        void upload(long id, Path file) throws Exception {
            if (fail) {
                throw new java.io.IOException("Simulated failed upload");
            }
            names.add(file.getFileName().toString());
        }
    }
}

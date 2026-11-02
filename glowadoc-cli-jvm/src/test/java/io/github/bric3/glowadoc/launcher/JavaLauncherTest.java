// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

class JavaLauncherTest {
    @TempDir
    Path directory;
    private Path home;
    private Path bin;
    private Path launcher;
    private Path capture;
    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    void prepareIsolatedEnvironment() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        home = Files.createDirectories(directory.resolve("home with spaces"));
        bin = Files.createDirectories(directory.resolve("commands"));
        capture = directory.resolve("arguments.txt");
        launcher = directory.resolve("reader with spaces.jar");
        Files.copy(Path.of(System.getProperty("launcher.executable")), launcher);
        assertTrue(launcher.toFile().setExecutable(true));
        for (String utility : List.of("uname", "mkdir", "mktemp", "rm", "mv", "readlink", "shasum", "sha256sum")) {
            Path source = Path.of("/usr/bin", utility);
            if (!Files.exists(source)) {
                source = Path.of("/bin", utility);
            }
            if (!Files.exists(source)) {
                source = Path.of("/sbin", utility);
            }
            if (Files.exists(source)) {
                Files.createSymbolicLink(bin.resolve(utility), source);
            }
        }
        environment.put("HOME", home.toString());
        environment.put("PATH", bin.toString());
        environment.put("LAUNCHER_CAPTURE", capture.toString());
        environment.put("FAKE_JAVA_EXIT", "0");
    }

    @Test
    void prefersMiseAndDoesNotQueryOrSearchVersions() throws Exception {
        Path selected = javaHome("mise selected", "mise");
        manager("mise", selected, "");
        javaHomeAt(home.resolve(".sdkman/candidates/java/current"), "sdkman");
        manager("asdf", javaHome("asdf selected", "asdf"), "");
        manager("jenv", javaHome("jenv selected", "jenv"), "");
        javaAt(bin.resolve("java"), "PATH");
        assertEquals(0, run("a file with spaces.adoc"));
        assertEquals("mise", arguments().getFirst());
        String calls = Files.readString(directory.resolve("manager-calls"));
        assertTrue(calls.startsWith("mise\nexec\njava@27\n--\njava\n"), calls);
        assertFalse(calls.contains("where"));
    }

    @TableTest("""
        name
        mise
        asdf
        jenv
        """)
    void delegatesToTheSelectedManagerAndPreservesArgumentsAndFailure(String name) throws Exception {
        manager(name, javaHome(name + " selected", name), "");
        environment.put("JAVA_OPTS", "-Xmx64m -Dexample=value");
        environment.put("FAKE_JAVA_EXIT", "23");
        assertEquals(23, run("a file with spaces.adoc", "$(touch should-not-exist)"));
        assertEquals(
                List.of(
                        name,
                        "-Dsun.misc.unsafe.memory.access=allow",
                        "-Xmx64m",
                        "-Dexample=value",
                        "-jar",
                        launcher.toString(),
                        "a file with spaces.adoc",
                        "$(touch should-not-exist)"
                ),
                arguments()
        );
        assertFalse(Files.exists(directory.resolve("should-not-exist")));
        String calls = Files.readString(directory.resolve("manager-calls"));
        assertTrue(calls.startsWith(name + "\nexec\n"), calls);
        assertFalse(calls.contains("/bin/sh"), calls);
    }

    @Test
    void usesSdkmanSelectedCandidateWithoutScanningOtherInstallations() throws Exception {
        Path candidates = directory.resolve("custom sdk candidates");
        environment.put("SDKMAN_CANDIDATES_DIR", candidates.toString());
        javaHomeAt(candidates.resolve("java/current"), "sdkman current");
        javaHomeAt(candidates.resolve("java/newer"), "sdkman other");
        manager("asdf", javaHome("asdf selected", "asdf"), "");
        environment.put("FAKE_JAVA_EXIT", "1");
        assertEquals(1, run());
        assertEquals("sdkman current", arguments().getFirst());
        assertFalse(Files.exists(directory.resolve("manager-calls")));
    }

    @Test
    void supportsSdkmanMacOsJavaHomeLayout() throws Exception {
        javaHomeAt(home.resolve(".sdkman/candidates/java/current/Contents/Home"), "macOS");
        assertEquals(0, run());
        assertEquals("macOS", arguments().getFirst());
    }

    @Test
    void prefersAsdfBeforeJenv() throws Exception {
        manager("asdf", javaHome("asdf selected", "asdf"), "");
        manager("jenv", javaHome("jenv selected", "jenv"), "");
        assertEquals(0, run());
        assertEquals("asdf", arguments().getFirst());
    }

    @Test
    void usesJabbaConfiguredJdkWithoutSearchingVersions() throws Exception {
        Path selected = javaHome("jabba selected", "jabba");
        script(
                bin.resolve("jabba"),
                "[ \"$1:$2\" = which:--home ] || exit 91\nprintf '%s\\n' " + quote(selected.toString()) + "\n"
        );
        assertEquals(0, run());
        assertEquals("jabba", arguments().getFirst());
    }

    @Test
    void usesPathAndIgnoresJavaHomeWhenNoManagerIsAvailable() throws Exception {
        environment.put("JAVA_HOME", javaHome("explicit", "JAVA_HOME").toString());
        javaAt(bin.resolve("java"), "PATH");
        environment.put("FAKE_JAVA_EXIT", "23");
        assertEquals(23, run("document.adoc"));
        assertEquals("PATH", arguments().getFirst());
    }

    @Test
    void unavailableSelectedManagerRuntimeFailsWithoutFallingBack() throws Exception {
        script(bin.resolve("mise"), "exit 42\n");
        javaAt(bin.resolve("java"), "PATH");
        assertEquals(42, run());
        assertFalse(Files.exists(capture));
    }

    @Test
    void missingJavaOnPathReportsTheShellFailure() throws Exception {
        assertEquals(127, run());
        assertTrue(Files.readString(directory.resolve("output.txt")).contains("java"));
        assertFalse(Files.exists(capture));
    }

    @Test
    void sharesTheFullArtifactHashAndReportsStagesWithoutParsingUtilities() throws Exception {
        usePathJava("selected");
        Path cache = Files.createDirectories(directory.resolve("aot cache/glow-adoc/aot"));
        Files.writeString(cache.resolve("obsolete.aot"), "old cache");
        Files.writeString(cache.resolve("0".repeat(64) + "-other.aot"), "other artifact");
        environment.put("XDG_CACHE_HOME", cache.getParent().getParent().toString());
        assertEquals(0, run("--train-aot", "document.adoc"));
        assertEquals(0, run("--timings", "document.adoc"));
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = new DigestInputStream(Files.newInputStream(launcher), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        assertTrue(
                arguments().contains("-Dglowadoc.launcher.artifact=" + launcher.toRealPath()),
                arguments().toString()
        );
        assertTrue(arguments().contains("-Dglowadoc.launcher.sha256=" + HexFormat.of().formatHex(digest.digest())));
        String output = Files.readString(directory.resolve("output.txt"));
        int previous = -1;
        for (String stage :
                List.of(
                        "runtime-identity",
                        "artifact-hash-start",
                        "artifact-hash-ready",
                        "cache-key-ready",
                        "cache-lookup-ready",
                        "jvm-exec"
        )) {
            int at = output.indexOf("glow-adoc launcher: " + stage);
            assertTrue(at > previous, stage + ": " + output);
            previous = at;
        }
        assertEquals(0, run("document.adoc"));
        assertFalse(Files.readString(directory.resolve("output.txt")).contains("glow-adoc launcher:"));
    }

    @Test
    void failedHashingLeavesOrdinaryLaunchAvailable() throws Exception {
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("failed-hash").toString());
        assertEquals(0, run("--train-aot", "document.adoc"));
        Files.deleteIfExists(bin.resolve("sha256sum"));
        script(bin.resolve("sha256sum"), "exit 1\n");
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments()
            .stream()
            .anyMatch(value -> value.startsWith("-Dglowadoc.launcher.")));
        assertEquals(2, run("--train-aot", "document.adoc"));
    }

    @TableTest("""
        utility
        sha256sum
        shasum
        """)
    void checksumUtilitiesProduceCompatibleAotIdentities(String utility) throws Exception {
        Assumptions.assumeTrue(
                Files.isExecutable(bin.resolve("sha256sum")) && Files.isExecutable(bin.resolve("shasum"))
        );
        Path sha256sum = bin.resolve("sha256sum").toRealPath();
        Path shasum = bin.resolve("shasum").toRealPath();
        Files.delete(bin.resolve("sha256sum"));
        Files.delete(bin.resolve("shasum"));
        Files.createSymbolicLink(bin.resolve(utility), utility.equals("sha256sum") ? sha256sum : shasum);
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("checksum-cache").toString());
        assertEquals(0, run("--train-aot"));
        assertEquals(0, run("document.adoc"));
        var checksum = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(launcher), checksum)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        String digest = HexFormat.of().formatHex(checksum.digest());
        assertTrue(arguments().contains("-Dglowadoc.launcher.sha256=" + digest));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));

        Files.delete(bin.resolve(utility));
        String alternative = utility.equals("sha256sum") ? "shasum" : "sha256sum";
        Files.createSymbolicLink(bin.resolve(alternative), alternative.equals("sha256sum") ? sha256sum : shasum);
        assertEquals(0, run("document.adoc"));
        assertTrue(arguments().contains("-Dglowadoc.launcher.sha256=" + digest));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
    }

    @Test
    void unrelatedAotFilesDoNotResolveTheRuntimeOrStartChecksumProcesses() throws Exception {
        usePathJava("selected");
        Path cache = Files.createDirectories(directory.resolve("unrelated/glow-adoc/aot"));
        environment.put("XDG_CACHE_HOME", cache.getParent().getParent().toString());
        Files.writeString(cache.resolve("obsolete.aot"), "old");
        assertEquals(0, run("--timings", "document.adoc"));
        String output = Files.readString(directory.resolve("output.txt"));
        assertFalse(output.contains("runtime-identity"), output);
        assertFalse(output.contains("artifact-hash-start"), output);
    }

    @TableTest("""
        option
        --help
        --version
        """)
    void helpAndVersionSkipAotWhenTimingsPrecedesThem(String option) throws Exception {
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("informational-aot").toString());
        assertEquals(0, run("--train-aot"));
        assertEquals(0, run("--timings", option));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
        assertFalse(Files.readString(directory.resolve("output.txt")).contains("artifact-hash-start"));
        assertEquals(0, run("--", option));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
    }

    @Test
    void missingJdkMetadataSkipsAutomaticAotWithoutProbingJava() throws Exception {
        Path selected = usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("missing-metadata").toString());
        assertEquals(0, run("--train-aot", "document.adoc"));
        Files.delete(selected.resolve("release"));
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
        assertEquals(2, run("--train-aot", "document.adoc"));
    }

    @Test
    void jdkMetadataChangesInvalidateAot() throws Exception {
        Path selected = usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("metadata-aot").toString());
        assertEquals(0, run("--train-aot", "document.adoc"));
        assertEquals(0, run("document.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
        Files.writeString(selected.resolve("release"), "IMPLEMENTOR=\"Another vendor\"\n", StandardOpenOption.APPEND);
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
    }

    @Test
    void trainsAndSelectsOnlyMatchingLocalCaches() throws Exception {
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("cache with spaces").toString());
        assertEquals(0, run("--train-aot", "fixture with spaces.adoc"));
        Path absoluteLauncher = launcher;
        launcher = Path.of(".").resolve(directory.relativize(launcher));
        assertEquals(0, run("document.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"), arguments().toString());
        launcher = absoluteLauncher;
        environment.put("JAVA_OPTS", "-Xmx128m");
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
        environment.remove("JAVA_OPTS");
        Files.writeString(launcher, "\n", StandardOpenOption.APPEND);
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
    }

    @Test
    void trainsBundledWorkloadWithoutAnInputFile() throws Exception {
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("bundled-aot").toString());
        assertEquals(0, run("--train-aot"));
        assertTrue(arguments().contains("--aot-workload"), arguments().toString());
        assertEquals(0, run("an-unseen-document.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
        assertEquals(2, run("--train-aot", "first.adoc", "second.adoc"));
    }

    @Test
    void reusesTrainingAfterRelocationWithTheSameExecutableName() throws Exception {
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("relocation-aot").toString());
        assertEquals(0, run("--train-aot"));
        Path relocated = Files.createDirectories(directory.resolve("installed bin")).resolve(launcher.getFileName());
        Files.copy(launcher, relocated, StandardCopyOption.COPY_ATTRIBUTES);
        launcher = relocated;
        assertEquals(0, run("other.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"), arguments().toString());
        Path renamed = relocated.resolveSibling("renamed.jar");
        Files.move(relocated, renamed);
        launcher = renamed;
        assertEquals(0, run("other.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
    }

    @Test
    void archiveLauncherSelectsJavaAndTracksTheJarForAotAfterRelocation() throws Exception {
        Path app = Files.createDirectories(directory.resolve("extracted archive"));
        launcher = Files.createDirectories(app.resolve("bin")).resolve("glow-adoc");
        Path jar = Files.createDirectories(app.resolve("lib")).resolve("glow-adoc.jar");
        try (var archive = new ZipFile(System.getProperty("glow.distribution"))) {
            for (Path target : List.of(launcher, jar)) {
                var entry = archive
                    .stream()
                    .filter(item -> item.getName().endsWith("/" + app.relativize(target)))
                    .findFirst()
                    .orElseThrow();
                try (var input = archive.getInputStream(entry)) {
                    Files.copy(input, target);
                }
            }
        }
        assertTrue(launcher.toFile().setExecutable(true));
        manager("mise", javaHome("mise selected", "mise"), "");
        environment.put("XDG_CACHE_HOME", directory.resolve("archive-aot").toString());
        assertEquals(0, run("--train-aot"));
        assertEquals("mise", arguments().getFirst());
        assertEquals(jar, Path.of(arguments().get(arguments().indexOf("-jar") + 1)).normalize());

        Path relocated = Files.move(app, directory.resolve("relocated archive"));
        launcher = Files.createSymbolicLink(
                directory.resolve("linked-glow-adoc"),
                directory.relativize(relocated.resolve("bin/glow-adoc"))
        );
        assertEquals(0, run("other.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"), arguments().toString());
        assertTrue(
                arguments().contains(
                        "-Dglowadoc.launcher.artifact=" + relocated.resolve("lib/glow-adoc.jar").toRealPath()
                ),
                arguments().toString()
        );
        Files.writeString(relocated.resolve("lib/glow-adoc.jar"), "changed", StandardOpenOption.APPEND);
        assertEquals(0, run("other.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
    }

    @TableTest("""
        name
        mise
        asdf
        jenv
        """)
    void aotUsesTheManagersRuntimeAndJvmEnvironment(String name) throws Exception {
        Path selected = javaHome(name + " selected", name);
        manager(name, selected, "export JAVA_TOOL_OPTIONS='-Dmanager.setting=one'\n");
        environment.put("XDG_CACHE_HOME", directory.resolve("manager-aot").toString());
        assertEquals(0, run("--train-aot", "document.adoc"));
        assertEquals(0, run("document.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"), arguments().toString());
        manager(name, selected, "export JAVA_TOOL_OPTIONS='-Dmanager.setting=two'\n");
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
        manager(name, selected, "export JAVA_TOOL_OPTIONS='-javaagent:example.jar'\n");
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
    }

    @TableTest("""
        name
        mise
        asdf
        jenv
        """)
    void aotUsesManagerJavaHomeWhenJavaIsAShim(String name) throws Exception {
        Path selected = javaHome("managed JDK", "managed");
        Path shims = Files.createDirectory(directory.resolve("shims"));
        script(shims.resolve("java"), "exit 93\n");
        manager(
                name,
                selected,
                "JAVA_HOME="
                + quote(selected.toString())
                + "\nexport JAVA_HOME\nPATH="
                + quote(shims.toString())
                + ":$PATH\nexport PATH\n"
        );
        environment.put("XDG_CACHE_HOME", directory.resolve("shim-aot").toString());
        assertEquals(0, run("--train-aot"));
        assertEquals(0, run("document.adoc"));
        assertEquals("managed", arguments().getFirst());
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
    }

    @Test
    void aotIgnoresUnselectedJavaHomeWhenUsingPath() throws Exception {
        usePathJava("PATH selected");
        environment.put("JAVA_HOME", javaHome("other JDK", "unselected").toString());
        environment.put("XDG_CACHE_HOME", directory.resolve("path-aot").toString());
        assertEquals(0, run("--train-aot"));
        assertEquals(0, run("document.adoc"));
        assertEquals("PATH selected", arguments().getFirst());
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
    }

    @TableTest("""
        name
        mise
        asdf
        jenv
        """)
    void aotKeepsTheManagersPathJavaWhenJavaHomeIsStale(String name) throws Exception {
        Path selected = javaHome("managed JDK", "managed");
        manager(name, selected, "");
        environment.put("JAVA_HOME", javaHome("other JDK", "unselected").toString());
        environment.put("XDG_CACHE_HOME", directory.resolve("manager-path-aot").toString());
        assertEquals(0, run("--train-aot"));
        assertEquals(0, run("document.adoc"));
        assertEquals("managed", arguments().getFirst());
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
    }

    @TableTest("""
        name
        mise
        asdf
        jenv
        """)
    void aotReusesTheManagersJavaHomeAcrossPathAliases(String name) throws Exception {
        Path selected = javaHome("managed JDK", "managed");
        Path alias = Files.createSymbolicLink(directory.resolve("current JDK"), selected);
        String setup = "export JAVA_HOME=" + quote(selected.toString()) + "\n";
        manager(name, alias, setup);
        environment.put("XDG_CACHE_HOME", directory.resolve("manager-alias-aot").toString());
        assertEquals(0, run("--train-aot"));
        manager(name, selected, setup);
        assertEquals(0, run("document.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
    }

    @TableTest("""
        variable
        JAVA_OPTS
        JAVA_TOOL_OPTIONS
        JDK_JAVA_OPTIONS
        _JAVA_OPTIONS
        """)
    void automaticJvmOptionsInvalidateAndBypassAotCaches(String variable) throws Exception {
        usePathJava("selected");
        environment.put("XDG_CACHE_HOME", directory.resolve("automatic-aot").toString());
        assertEquals(0, run("--train-aot", "document.adoc"));
        assertEquals(0, run("document.adoc"));
        assertTrue(arguments().contains("-XX:AOTMode=auto"));
        environment.put(variable, "-Xmx128m");
        assertEquals(0, run("document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
        environment.put(variable, "-javaagent:example.jar");
        assertEquals(0, run("--timings", "document.adoc"));
        assertFalse(arguments().contains("-XX:AOTMode=auto"));
        assertFalse(Files.readString(directory.resolve("output.txt")).contains("artifact-hash-start"));
    }

    @Test
    void corruptCacheFallsBackWithoutWritingJvmDiagnosticsToStdout() throws Exception {
        Path selected = usePathJava("selected");
        String realJava = quote(Path.of(System.getProperty("java.home"), "bin/java").toString());
        environment.put("XDG_CACHE_HOME", directory.resolve("aot cache").toString());
        Path document = Files.writeString(directory.resolve("document.adoc"), "= Hello\n\nAOT fallback\n");
        script(
                selected.resolve("bin/java"),
                "for option do\n"
                + "  case \"$option\" in -XX:AOTCacheOutput=*) printf corrupt >"
                + " \"${option#*=}\" ;; esac\n"
                + "done\n"
                + "exit 0\n"
        );
        assertEquals(0, run("--train-aot", document.toString()));
        script(selected.resolve("bin/java"), "exec " + realJava + " \"$@\"\n");
        Path stdout = directory.resolve("stdout");
        Path stderr = directory.resolve("stderr");
        var builder = new ProcessBuilder(launcher.toString(), "--no-pager", "--color=never", document.toString())
            .directory(directory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        var process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(stderr));
            assertEquals("Hello\n\nAOT fallback\n\n", Files.readString(stdout));
            assertTrue(Files.readString(stderr).contains("[aot]"));
        } finally {
            process.destroyForcibly();
        }
    }

    private int run(String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(launcher.toString());
        command.addAll(Arrays.asList(args));
        var builder = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(directory.resolve("output.txt").toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        var process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Launcher timed out");
            return process.exitValue();
        } finally {
            process.destroyForcibly();
        }
    }

    private List<String> arguments() throws IOException {
        return Files.readAllLines(capture);
    }

    private Path usePathJava(String label) throws IOException {
        Path selected = javaHome(label, label);
        environment.put("PATH", selected.resolve("bin") + ":" + bin);
        return selected;
    }

    private Path javaHome(String name, String label) throws IOException {
        Path result = directory.resolve(name);
        javaHomeAt(result, label);
        return result;
    }

    private void javaHomeAt(Path path, String label) throws IOException {
        javaAt(path.resolve("bin/java"), label);
        Files.writeString(path.resolve("release"), "JAVA_VERSION=\"27\"\nIMPLEMENTOR=\"Example\"\n");
    }

    private void javaAt(Path path, String label) throws IOException {
        script(
                path,
                String.join(
                        "\n",
                        "for option do",
                        "  case \"$option\" in",
                        "    -jar) break ;;",
                        "    -version|--version|-XshowSettings*) exit 92 ;;",
                        "    -XX:AOTCacheOutput=*) printf cache > \"${option#*=}\" ;;",
                        "  esac",
                        "done",
                        "printf '%s\\n' " + quote(label) + " \"$@\" > \"$LAUNCHER_CAPTURE\"",
                        "exit \"$FAKE_JAVA_EXIT\"",
                        ""
                )
        );
    }

    private void manager(String name, Path selected, String setup) throws IOException {
        script(
                bin.resolve(name),
                String.join(
                        "\n",
                        "printf '%s\\n' " + quote(name) + " \"$@\" >> " + quote(directory
                            .resolve("manager-calls")
                            .toString()
                        ),
                        "[ \"$MISE_OFFLINE:$MISE_AUTO_INSTALL:$MISE_EXEC_AUTO_INSTALL\" = 1:0:0 ]" + " || exit 90",
                        "if [ \"$1\" = exec ]; then",
                        "  shift",
                        "  if [ " + quote(name) + " = mise ] && [ \"${1:-}\" = java@27 ]; then shift; fi",
                        "  [ \"${1:-}\" != -- ] || shift",
                        "  [ " + quote(name) + " != asdf ] || [ \"$1\" = java ] || exit 94",
                        "elif [ " + quote(name) + " = asdf ] && [ \"$1:$2\" = env:java ]; then",
                        "  shift 2",
                        "else",
                        "  exit 91",
                        "fi",
                        "PATH=" + quote(selected.resolve("bin").toString()) + ":$PATH",
                        "export PATH",
                        setup,
                        "exec \"$@\"",
                        ""
                )
        );
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void script(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "#!/bin/sh\n" + contents, StandardCharsets.UTF_8);
        if (!path.toFile().setExecutable(true)) {
            throw new IOException("Cannot make executable: " + path);
        }
    }
}

// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class LinkOpenerTest {
    @TempDir
    Path directory;

    @Test
    void passesTheUrlAsOneLiteralArgumentToTheOpener() throws Exception {
        Path result = directory.resolve("opened");
        String target = "https://example.org/path?q=$(echo%20literal)&page=1#section";
        try (var opener = opener(result, "record", false)) {
            opener.open(target);
            assertEquals("Opened link", diagnostic(opener));
            assertEquals(target, Files.readString(result).lines().skip(1).findFirst().orElseThrow());
        }
        assertEquals(List.of("/usr/bin/open", "--"), LinkOpener.command("Mac OS X"));
        assertEquals(List.of("xdg-open"), LinkOpener.command("Linux"));
    }

    @Test
    void unsafeAndMalformedLinksNeverStartAProcess() throws Exception {
        Path result = directory.resolve("not-opened");
        try (var opener = opener(result, "record", false)) {
            for (String target :
                    List.of(
                            "file:///etc/passwd",
                            "javascript:alert(1)",
                            "https://",
                            "https://example.org/has space",
                            "https://example.org/\u001B"
            )) {
                opener.open(target);
                assertEquals("Cannot open this link", diagnostic(opener));
            }
        }
        assertFalse(Files.exists(result));
    }

    @Test
    void sshUsesNativeTerminalLinksWithoutLaunchingARemoteBrowser() throws Exception {
        Path result = directory.resolve("remote");
        try (var opener = opener(result, "record", true)) {
            opener.open("https://example.org");
            assertTrue(diagnostic(opener).startsWith("SSH:"));
        }
        assertFalse(Files.exists(result));
        assertTrue(LinkOpener.remote(Map.of("SSH_CONNECTION", "test")));
        assertFalse(LinkOpener.remote(Map.of("SSH_TTY", "")));
    }

    @Test
    void timeoutsAndClosingTerminateOnlyTheOpener() throws Exception {
        for (boolean close : List.of(false, true)) {
            Path result = directory.resolve("pid-" + close);
            try (var opener = opener(result, "hang", false)) {
                opener.open("https://example.org");
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                    while (!Files.exists(result) || Files.size(result) == 0) {
                        Thread.sleep(10);
                    }
                    long pid = Long.parseLong(Files.readString(result).lines().findFirst().orElseThrow());
                    if (close) {
                        opener.close();
                    } else {
                        assertEquals("Browser opener timed out", diagnostic(opener));
                    }
                    while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                        Thread.sleep(10);
                    }
                });
            }
        }
    }

    private LinkOpener opener(Path result, String mode, boolean remote) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin/java").toString();
        String classes =
                Path.of(LinkOpenerTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new LinkOpener(
                List.of(java, "-cp", classes, FakeOpener.class.getName(), result.toString(), mode),
                remote
        );
    }

    private static String diagnostic(LinkOpener opener) {
        return assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (true) {
                var messages = opener.takeDiagnostics();
                if (!messages.isEmpty()) {
                    return messages.getFirst();
                }
                Thread.sleep(10);
            }
        });
    }

    public static class FakeOpener {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]), ProcessHandle.current().pid() + "\n" + args[2]);
            if (args[1].equals("hang")) {
                Thread.sleep(60_000);
            }
        }
    }
}

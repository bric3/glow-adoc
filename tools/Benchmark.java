// SPDX-License-Identifier: MPL-2.0
//JAVA 27

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Fresh-process timings; --pty measures the first complete pager frame. */
public class Benchmark {
    public static void main(String[] args) throws Exception {
        int runs = 10;
        boolean pty = false;
        boolean scroll = false;
        boolean emptyCache = false;
        int start = 0;
        for (; start < args.length && !args[start].equals("--"); start++) {
            if (args[start].equals("--runs")) {
                runs = Integer.parseInt(args[++start]);
            } else if (args[start].equals("--pty")) {
                pty = true;
            } else if (args[start].equals("--scroll")) {
                scroll = true;
                pty = true;
            } else if (args[start].equals("--empty-cache")) {
                emptyCache = true;
            } else {
                throw new IllegalArgumentException(
                        "Usage: Benchmark.java [--runs N] [--pty|--scroll] [--empty-cache] --"
                            + " COMMAND [ARGS]");
            }
        }
        if (runs < 1 || start + 1 >= args.length) {
            throw new IllegalArgumentException("Supply a command and a positive run count");
        }
        List<String> command = Arrays.asList(args).subList(start + 1, args.length);
        var times = new ArrayList<Double>();
        System.out.println("run,milliseconds");
        for (int run = 0; run < runs; run++) {
            Path transcript = Files.createTempFile("glow-benchmark-", ".log");
            String invocation =
                    "stty cols 80 rows 24 && exec "
                            + command.stream()
                                    .map(Benchmark::quote)
                                    .collect(Collectors.joining(" "));
            var builder =
                    new ProcessBuilder(
                            pty
                                    ? System.getProperty("os.name").startsWith("Mac")
                                            ? List.of(
                                                    "/usr/bin/script",
                                                    "-q",
                                                    "/dev/null",
                                                    "/bin/sh",
                                                    "-c",
                                                    invocation)
                                            : List.of(
                                                    "/usr/bin/script",
                                                    "-q",
                                                    "-e",
                                                    "-c",
                                                    invocation,
                                                    "/dev/null")
                                    : command);
            if (pty) {
                builder.redirectOutput(transcript.toFile());
            } else {
                builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            }
            builder.redirectError(transcript.toFile());
            if (pty) {
                builder.redirectErrorStream(true);
            }
            builder.environment().put("TERM", "xterm-256color");
            builder.environment().put("COLORTERM", "truecolor");
            builder.environment().remove("NO_COLOR");
            Path cache = emptyCache ? Files.createTempDirectory("glow-benchmark-cache-") : null;
            if (cache != null) {
                builder.environment().put("XDG_CACHE_HOME", cache.toString());
            }
            long begin = System.nanoTime();
            Process process = builder.start();
            try {
                if (pty) {
                    while (!Files.readString(transcript).contains("q quit")) {
                        if (!process.isAlive()
                                || System.nanoTime() - begin > TimeUnit.SECONDS.toNanos(60)) {
                            throw new IllegalStateException(
                                    "No first frame; exit/alive=" + process.isAlive());
                        }
                        Thread.sleep(5);
                    }
                } else if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    throw new IllegalStateException(
                            "Command failed: " + Files.readString(transcript));
                }
                double elapsed = (System.nanoTime() - begin) / 1_000_000.0;
                times.add(elapsed);
                System.out.printf(Locale.ROOT, "%d,%.1f%n", run + 1, elapsed);
                if (pty) {
                    if (scroll) {
                        measureScrolling(process, transcript, run + 1);
                    }
                    process.getOutputStream().write('q');
                    process.getOutputStream().flush();
                    if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                        throw new IllegalStateException("Pager did not exit successfully");
                    }
                }
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                Files.deleteIfExists(transcript);
                if (cache != null) {
                    try (var files = Files.walk(cache)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
            }
        }
        times.sort(Double::compareTo);
        System.out.printf(
                Locale.ROOT,
                "median,%.1f%np95,%.1f%n",
                (times.get((times.size() - 1) / 2) + times.get(times.size() / 2)) / 2,
                times.get(Math.min(times.size() - 1, (int) Math.ceil(times.size() * .95) - 1)));
    }

    private static void measureScrolling(Process process, Path transcript, int run)
            throws Exception {
        process.getOutputStream()
                .write("\u001b_Gi=2147483000;OK\u001b\\".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!Files.readString(transcript).contains("a=p,i=")
                && !Files.readString(transcript).contains("a=T,t=d")) {
            if (!process.isAlive() || System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "No visible image; --scroll requires an image in the initial viewport");
            }
            Thread.sleep(5);
        }
        Thread.sleep(200);
        int before = Files.readString(transcript).length();
        long bytes = Files.size(transcript);
        var frames = new ArrayList<Double>();
        for (int key = 0; key < 20; key++) {
            int offset = Files.readString(transcript).length();
            long begin = System.nanoTime();
            process.getOutputStream().write('j');
            process.getOutputStream().flush();
            while (!Files.readString(transcript).substring(offset).contains("\u001b[?2026l")) {
                if (!process.isAlive()
                        || System.nanoTime() - begin > TimeUnit.SECONDS.toNanos(10)) {
                    throw new IllegalStateException("Scroll frame timed out");
                }
                Thread.sleep(2);
            }
            frames.add((System.nanoTime() - begin) / 1_000_000.0);
        }
        Thread.sleep(200);
        String changes = Files.readString(transcript).substring(before);
        long uploads = Pattern.compile("a=[tT],t=d,f=100").matcher(changes).results().count();
        frames.sort(Double::compareTo);
        System.out.printf(
                Locale.ROOT,
                "scroll-p95-%d,%.1f%nscroll-bytes-%d,%d%nscroll-uploads-%d,%d%n",
                run,
                frames.get(18),
                run,
                Files.size(transcript) - bytes,
                run,
                uploads);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}

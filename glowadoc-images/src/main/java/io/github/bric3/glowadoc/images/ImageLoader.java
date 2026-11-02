// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.images;

import com.github.weisj.jsvg.parser.DocumentLimits;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;

/**
 * Session-owned SVG images, requested after the first terminal frame.
 */
public final class ImageLoader implements AutoCloseable {
    private static final int MAX_SOURCE = 256 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_PIXELS = 2_000_000;

    private record Key(String target, double scale) {}

    private final Path directory;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final Map<Key, CompletableFuture<Optional<Path>>> cache = new ConcurrentHashMap<>();
    // The serial worker reuses source bytes when a terminal changes raster density.
    private final Map<String, byte[]> sources = new HashMap<>();
    private final Queue<String> diagnostics = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile HttpClient client;
    private volatile boolean closed;

    public ImageLoader() throws IOException {
        this(null);
    }

    ImageLoader(HttpClient client) throws IOException {
        this.client = client;
        directory = Files.createTempDirectory("glow-adoc-images-");
    }

    public static boolean supports(String target, Path local) {
        if (local != null) {
            return local.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".svg");
        }
        if (target.length() > 2048) {
            return false;
        }
        try {
            URI uri = URI.create(target);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getPort() != -1 && uri.getPort() != 443) {
                return false;
            }
            // Fetch only trusted badge services; document URLs must not probe arbitrary hosts.
            return "img.shields.io".equalsIgnoreCase(uri.getHost())
                    || "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath().matches("/[^/]+/[^/]+/actions/workflows/[^/]+/badge\\.svg");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Requests an image; local paths must already have passed the caller's image-root checks.
     * Scale maps SVG logical pixels to raster pixels within the image size limits.
     */
    public Optional<Path> request(String target, Path local, double scale) {
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("SVG scale must be finite and positive");
        }
        var key = new Key(target, scale);
        if (closed || !supports(target, local) || cache.size() >= 128 && !cache.containsKey(key)) {
            return Optional.empty();
        }
        cache.computeIfAbsent(key, ignored -> {
            var task = CompletableFuture.supplyAsync(() -> load(key, local), worker);
            // Reflow must read the completed task, rather than its unfinished notification stage.
            task.whenComplete((result, error) -> {
                if (!closed) {
                    dirty.set(true);
                }
            });
            return task;
        });
        return cached(target, scale);
    }

    public Optional<Path> cached(String target, double scale) {
        var future = cache.get(new Key(target, scale));
        return closed || future == null || !future.isDone() || future.isCompletedExceptionally()
                ? Optional.empty()
                : future.getNow(Optional.empty());
    }

    public boolean takeDirty() {
        return dirty.getAndSet(false);
    }

    public List<String> takeDiagnostics() {
        var result = new ArrayList<String>();
        String message;
        while ((message = diagnostics.poll()) != null) {
            result.add(message);
        }
        return result;
    }

    private Optional<Path> load(Key key, Path local) {
        try {
            byte[] source = sources.get(key.target());
            if (source == null) {
                if (local != null) {
                    try (var input = Files.newInputStream(local)) {
                        source = input.readNBytes(MAX_SOURCE + 1);
                    }
                } else {
                    source = download(URI.create(key.target()));
                }
                if (source.length > MAX_SOURCE) {
                    throw new IOException("SVG source exceeds size limit");
                }
                sources.put(key.target(), source);
            }
            var xml = XMLInputFactory.newFactory();
            xml.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xml.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            var context = LoaderContext
                .builder()
                .externalResourcePolicy(ResourcePolicy.DENY_ALL)
                .documentLimits(new DocumentLimits(30, 15, 2000))
                .build();
            byte[] svgSource = source;
            var svg = new SVGLoader()
                .load(
                        () -> xml.createXMLEventReader(new ByteArrayInputStream(svgSource)),
                        URI.create("file:/image.svg"),
                        context
                );
            if (svg == null) {
                throw new IOException("Invalid SVG");
            }
            int width = (int) Math.ceil(svg.size().width);
            int height = (int) Math.ceil(svg.size().height);
            if (width <= 0
                    || height <= 0
                    || width > MAX_DIMENSION
                    || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) {
                throw new IOException("SVG dimensions exceed limits");
            }
            double scale =
                    Math.min(key.scale(), Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height));
            scale = Math.min(scale, Math.sqrt((double) MAX_PIXELS / width / height));
            // Round down to keep scaled rasters within the dimension and pixel budgets.
            int rasterWidth = Math.max(1, (int) Math.floor(width * scale));
            int rasterHeight = Math.max(1, (int) Math.floor(height * scale));
            var image = new BufferedImage(rasterWidth, rasterHeight, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                double density = Math.min((double) rasterWidth / width, (double) rasterHeight / height);
                graphics.scale(density, density);
                svg.render(null, graphics);
            } finally {
                graphics.dispose();
            }
            synchronized (this) {
                if (closed) {
                    return Optional.empty();
                }
                Path png = Files.createTempFile(directory, "image-", ".png");
                ImageIO.write(image, "png", png.toFile());
                return Optional.of(png);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!closed) {
                diagnostics.add("SVG image unavailable");
            }
        }
        return Optional.empty();
    }

    private byte[] download(URI uri) throws Exception {
        HttpClient active = client;
        if (active == null) {
            active = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
            client = active;
            if (closed) {
                active.shutdownNow();
                throw new InterruptedException();
            }
        }
        var request = HttpRequest
            .newBuilder(uri)
            .timeout(Duration.ofSeconds(3))
            .header("Accept", "image/svg+xml")
            .header("User-Agent", "glow-adoc")
            .GET()
            .build();
        var future = active.sendAsync(
                request,
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_SOURCE)
        );
        try {
            var response = future.get(3, TimeUnit.SECONDS);
            if (response.statusCode() != 200) {
                throw new IOException("Badge HTTP status " + response.statusCode());
            }
            return response.body();
        } finally {
            future.cancel(true);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        worker.shutdownNow();
        HttpClient active = client;
        if (active != null) {
            active.shutdownNow();
        }
        try {
            worker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}

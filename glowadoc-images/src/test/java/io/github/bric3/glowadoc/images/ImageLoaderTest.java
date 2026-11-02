// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.images;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tabletest.junit.TableTest;

class ImageLoaderTest {
    private static final String BADGE = "https://img.shields.io/badge/test-passing-green";
    private static final String SVG =
            "<svg xmlns='http://www.w3.org/2000/svg' width='30' height='20'><rect width='30' "
            + "height='20' fill='#ff0000'/></svg>";
    @TempDir
    Path directory;

    @Test
    void rasterisesSvgAsynchronouslyAndDeletesItsOutputOnClose() throws Exception {
        Path svg = Files.writeString(directory.resolve("badge.svg"), SVG);
        Path rendered;
        try (var loader = new ImageLoader()) {
            assertTrue(ImageLoader.supports(svg.toString(), svg));
            loader.request(svg.toString(), svg, 1.0);
            rendered = await(loader, svg.toString(), 1.0).orElseThrow();
            var image = ImageIO.read(rendered.toFile());
            assertEquals(30, image.getWidth());
            assertEquals(20, image.getHeight());
            assertEquals(0xffff0000, image.getRGB(1, 1));
            assertEquals(Optional.of(rendered), loader.request(svg.toString(), svg, 1.0));
        }
        assertFalse(Files.exists(rendered));
    }

    @Test
    void downloadsAnSvgBadgeAndCachesItsRenderedPixels() throws Exception {
        var client = new BadgeClient();
        client.reply.complete(new Reply(200, SVG.getBytes(StandardCharsets.UTF_8)));
        try (var loader = new ImageLoader(client)) {
            loader.request(BADGE, null, 1.0);
            Path png = await(loader, BADGE, 1.0).orElseThrow();
            assertEquals(0xffff0000, ImageIO.read(png.toFile()).getRGB(1, 1));
            assertEquals(URI.create(BADGE), client.request.uri());
            assertEquals(Duration.ofSeconds(3), client.request.timeout().orElseThrow());
            assertEquals(Optional.of(png), loader.request(BADGE, null, 1.0));
            assertTrue(loader.takeDiagnostics().isEmpty());
        }
    }

    @TableTest(
            """
        scale | width | height
        0.5   | 15    | 10
        1.0   | 30    | 20
        1.5   | 45    | 30
        2.0   | 60    | 40
        3.0   | 90    | 60
        """
    )
    void rasterisesSvgAtTheRequestedDensity(double scale, int width, int height) throws Exception {
        Path svg = Files.writeString(directory.resolve("density.svg"), SVG);
        try (var loader = new ImageLoader()) {
            loader.request(svg.toString(), svg, scale);
            var image = ImageIO.read(await(loader, svg.toString(), scale).orElseThrow().toFile());
            assertEquals(width, image.getWidth());
            assertEquals(height, image.getHeight());
            assertEquals(0xffff0000, image.getRGB(1, 1));
        }
    }

    @Test
    void densityChangesReuseDownloadedSourceAndKeepSeparateRasters() throws Exception {
        var client = new BadgeClient();
        client.reply.complete(new Reply(200, SVG.getBytes(StandardCharsets.UTF_8)));
        try (var loader = new ImageLoader(client)) {
            loader.request(BADGE, null, 1.0);
            Path normal = await(loader, BADGE, 1.0).orElseThrow();
            loader.request(BADGE, null, 2.0);
            Path retina = await(loader, BADGE, 2.0).orElseThrow();
            assertNotEquals(normal, retina);
            assertEquals(30, ImageIO.read(normal.toFile()).getWidth());
            assertEquals(60, ImageIO.read(retina.toFile()).getWidth());
            assertEquals(Optional.of(normal), loader.cached(BADGE, 1.0));
            assertEquals(Optional.of(retina), loader.request(BADGE, null, 2.0));
            assertEquals(1, client.requests);
        }
    }

    @TableTest(
            """
        width | height | scale | rasterWidth | rasterHeight
        1000  | 1000   | 2.0   | 1414        | 1414
        4096  | 1      | 2.0   | 4096        | 1
        1     | 4096   | 2.0   | 1           | 4096
        """
    )
    void densityScalingKeepsRasterDimensionsBounded(
            int width,
            int height,
            double scale,
            int rasterWidth,
            int rasterHeight
    ) throws Exception {
        Path svg = Files.writeString(
                directory.resolve("large.svg"),
                "<svg xmlns='http://www.w3.org/2000/svg' width='" + width + "' height='" + height + "'/>"
        );
        try (var loader = new ImageLoader()) {
            loader.request(svg.toString(), svg, scale);
            var image = ImageIO.read(await(loader, svg.toString(), scale).orElseThrow().toFile());
            assertEquals(rasterWidth, image.getWidth());
            assertEquals(rasterHeight, image.getHeight());
            assertTrue((long) image.getWidth() * image.getHeight() <= 2_000_000);
        }
    }

    @TableTest("""
        scale
        0.0
        -1.0
        NaN
        Infinity
        """)
    void rejectsInvalidDensity(double scale) throws Exception {
        try (var loader = new ImageLoader()) {
            assertThrows(IllegalArgumentException.class, () -> loader.request(BADGE, null, scale));
        }
    }

    @TableTest("""
        status | bytes
        302    | 100
        404    | 100
        200    | 262145
        """)
    void failedOrOversizedDownloadsKeepTheFallback(int status, int bytes) throws Exception {
        var client = new BadgeClient();
        client.reply.complete(new Reply(status, new byte[bytes]));
        try (var loader = new ImageLoader(client)) {
            loader.request(BADGE, null, 1.0);
            assertTrue(await(loader, BADGE, 1.0).isEmpty());
            assertFalse(loader.takeDiagnostics().isEmpty());
        }
    }

    @Test
    void slowDownloadsDoNotBlockRequestsAndAreCancelledOnClose() throws Exception {
        var client = new BadgeClient();
        var loader = new ImageLoader(client);
        try (loader) {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertTrue(loader
                .request(BADGE, null, 1.0)
                .isEmpty()
            ));
            assertTrue(client.started.await(5, TimeUnit.SECONDS));
            assertFalse(loader.takeDirty());
        }
        assertTrue(client.reply.isCancelled());
        assertTrue(loader.cached(BADGE, 1.0).isEmpty());
    }

    @TableTest(
            """
        target                                                                       | supported
        https://img.shields.io/badge/license-MIT-blue                                | true
        https://github.com/owner/repo/actions/workflows/ci.yml/badge.svg?branch=main | true
        https://example.org/badge.svg                                                | false
        http://img.shields.io/badge/test                                             | false
        https://img.shields.io:444/badge/test                                        | false
        https://user@img.shields.io/badge/test                                       | false
        https://github.com/owner/repo                                                | false
        """
    )
    void recognisesTrustedHttpsBadgeSources(String target, boolean supported) {
        assertEquals(supported, ImageLoader.supports(target, null));
    }

    @TableTest(
            """
        source
        '<svg xmlns="http://www.w3.org/2000/svg" width="999999" height="999999"/>'
        '<!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:/etc/passwd">]><svg xmlns="http://www.w3.org/2000/svg"><text>&xxe;</text></svg>'
        '<svg><broken>'
        """
    )
    void rejectsUnsafeOrMalformedSvgAndKeepsAReadableFallback(String source) throws Exception {
        Path svg = Files.writeString(directory.resolve("invalid.svg"), source);
        try (var loader = new ImageLoader()) {
            loader.request(svg.toString(), svg, 1.0);
            assertTrue(await(loader, svg.toString(), 1.0).isEmpty());
            assertFalse(loader.takeDiagnostics().isEmpty());
        }
    }

    @Test
    void deniesSvgExternalResources() throws Exception {
        Path image = directory.resolve("secret.png");
        var red = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        red.setRGB(1, 1, 0xffff0000);
        ImageIO.write(red, "png", image.toFile());
        Path svg = Files.writeString(
                directory.resolve("external.svg"),
                "<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'><image href='"
                + image.toUri()
                + "' width='20' height='20'/></svg>"
        );
        try (var loader = new ImageLoader()) {
            loader.request(svg.toString(), svg, 1.0);
            var rendered = ImageIO.read(await(loader, svg.toString(), 1.0).orElseThrow().toFile());
            assertEquals(0, rendered.getRGB(1, 1));
        }
    }

    private static Optional<Path> await(ImageLoader loader, String target, double scale) {
        return assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!loader.takeDirty()) {
                Thread.sleep(5);
            }
            return loader.cached(target, scale);
        });
    }

    private record Reply(int statusCode, byte[] body) implements HttpResponse<byte[]> {
        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create(BADGE)).build();
        }

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create(BADGE);
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }

    private static final class BadgeClient extends HttpClient {
        private final CompletableFuture<HttpResponse<byte[]>> reply = new CompletableFuture<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile HttpRequest request;
        private int requests;

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler
        ) {
            this.request = request;
            requests++;
            started.countDown();
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) reply;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException,
                InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }
}

// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.terminal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.terminal.Backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

class KittyImagesTest {
    @Test
    void dimmingPreservesPixelsAndAlphaAndReusesBothVariants() throws Exception {
        Path png = directory.resolve("alpha.png");
        var picture = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(0, 0, 0xffff0000);
        picture.setRGB(1, 0, 0x8000ff00);
        ImageIO.write(picture, "png", png.toFile());
        try (var kitty = new KittyImages(backend)) {
            var dim = new KittyImages.Placement(1, png, 0, 0, 2, 1, 0, 1, true);
            var original = new KittyImages.Placement(1, png, 0, 0, 2, 1, 0, 1);
            kitty.render(List.of(dim));
            var payload = Pattern.compile("m=0;([A-Za-z0-9+/=]+)\u001b\\\\").matcher(output);
            assertTrue(payload.find());
            var decoded =
                    ImageIO.read(
                            new java.io.ByteArrayInputStream(
                                    Base64.getDecoder().decode(payload.group(1))));
            assertEquals(166, decoded.getRGB(0, 0) >>> 24);
            assertEquals(83, decoded.getRGB(1, 0) >>> 24);
            assertEquals(0xff0000, decoded.getRGB(0, 0) & 0xffffff);
            kitty.render(List.of(original));
            output.setLength(0);
            kitty.render(List.of(dim));
            assertFalse(output.toString().contains("a=t"));
            assertTrue(output.toString().contains("a=d,d=i,"));
            output.setLength(0);
            kitty.render(List.of(original));
            assertFalse(output.toString().contains("a=t"));
        }
    }

    @Test
    void imageBudgetIncludesTheUnusedVariantOfAVisibleImage() throws Exception {
        Path png = directory.resolve("budget.png");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        try (var kitty = new KittyImages(backend, 400)) {
            var dim = new KittyImages.Placement(1, png, 0, 0, 10, 10, 0, 10, true);
            var original = new KittyImages.Placement(1, png, 0, 0, 10, 10, 0, 10);
            kitty.render(List.of(dim));
            output.setLength(0);
            kitty.render(List.of(original));
            assertTrue(output.toString().contains("a=d,d=I,"), "Evict the unused dimmed pixels");
            output.setLength(0);
            kitty.render(List.of(dim));
            assertTrue(output.toString().contains("a=t"), "Reload the evicted variant");
        }
    }

    @TempDir Path directory;
    private final StringBuilder output = new StringBuilder();
    private final Backend backend =
            (Backend)
                    Proxy.newProxyInstance(
                            Backend.class.getClassLoader(),
                            new Class<?>[] {Backend.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("writeRaw")) {
                                    output.append(args[0]);
                                }
                                return null;
                            });

    @Test
    void scrollingReusesPixelsAndUnchangedFramesEmitNothing() throws Exception {
        Path png = directory.resolve("graph.png");
        var picture = new BufferedImage(80, 100, BufferedImage.TYPE_INT_RGB);
        var random = new Random(42);
        // Incompressible pixels exercise multiple protocol chunks.
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 80; x++) {
                picture.setRGB(x, y, random.nextInt());
            }
        }
        ImageIO.write(picture, "png", png.toFile());
        try (var kitty = new KittyImages(backend)) {
            var initial = new KittyImages.Placement(1, png, 0, 2, 10, 4, 0, 10);
            kitty.render(List.of(initial));
            assertTrue(output.toString().contains("a=t,t=d,f=100"));
            assertTrue(output.toString().contains("m=1;"));
            var payload = new StringBuilder();
            var chunks =
                    Pattern.compile("\u001b_G(?:a=t[^;]+)?m=[01];([A-Za-z0-9+/=]*)\u001b\\\\")
                            .matcher(output);
            while (chunks.find()) {
                payload.append(chunks.group(1));
            }
            assertArrayEquals(
                    Files.readAllBytes(png), Base64.getDecoder().decode(payload.toString()));
            output.setLength(0);
            kitty.render(List.of(initial));
            assertEquals("", output.toString());
            kitty.render(List.of(new KittyImages.Placement(1, png, 0, 2, 10, 4, 2, 10)));
            assertFalse(output.toString().contains("a=t"));
            assertTrue(output.toString().contains("y=20,w=80,h=40,c=10,r=4"));
            output.setLength(0);
            kitty.refreshPlacements();
            kitty.render(List.of(initial));
            assertTrue(output.toString().contains("a=p,i="));
            assertFalse(output.toString().contains("a=t"));
            output.setLength(0);
            kitty.render(List.of());
            assertTrue(output.toString().contains("a=d,d=i,"));
            assertFalse(output.toString().contains("a=d,d=I,"));
            output.setLength(0);
            kitty.render(List.of(initial));
            assertFalse(output.toString().contains("a=t"));
        }
        assertTrue(output.toString().contains("a=d,d=I,"));
    }

    @Test
    void terminalEvictionRetransmitsOnlyTheMissingImage() throws Exception {
        Path png = directory.resolve("graph.png");
        ImageIO.write(new BufferedImage(80, 100, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        try (var kitty = new KittyImages(backend)) {
            var placement = new KittyImages.Placement(1, png, 0, 0, 10, 4, 0, 10);
            kitty.render(List.of(placement));
            var header = Pattern.compile("a=t,t=d,f=100,q=2,i=([0-9]+)").matcher(output);
            assertTrue(header.find());
            int id = Integer.parseInt(header.group(1));
            output.setLength(0);
            kitty.invalidate(id);
            kitty.render(List.of(placement));
            assertTrue(output.toString().contains("a=t,t=d,f=100"));
        }
    }

    @Test
    void evictionReleasesOffscreenDataAndJpegConversionHappensOnce() throws Exception {
        Path jpeg = directory.resolve("graph.jpg");
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg.toFile());
        try (var kitty = new KittyImages(backend, 100)) {
            var placement = new KittyImages.Placement(1, jpeg, 0, 0, 10, 10, 0, 10);
            kitty.render(List.of(placement));
            output.setLength(0);
            kitty.render(List.of());
            assertTrue(output.toString().contains("a=d,d=I,"));
            output.setLength(0);
            kitty.render(List.of(placement));
            assertTrue(output.toString().contains("a=t,t=d,f=100"));
        }
    }
}

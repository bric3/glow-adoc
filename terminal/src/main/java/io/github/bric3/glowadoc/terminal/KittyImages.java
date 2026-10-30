// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.terminal;

import dev.tamboui.terminal.Backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import javax.imageio.ImageIO;

public final class KittyImages implements AutoCloseable {
    private final Backend backend;
    private int nextId = 1 + new SecureRandom().nextInt(1_000_000_000);
    private final java.util.List<Integer> ids = new java.util.ArrayList<>();

    public KittyImages(Backend backend) {
        this.backend = backend;
    }

    public record Size(int columns, int rows) {}

    public static Size measure(Path file, int width, int cellWidth, int cellHeight)
            throws IOException {
        try (var stream = ImageIO.createImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if ((long) w * h > 20_000_000L) {
                    throw new IOException("Image exceeds 20 megapixels");
                }
                int columns = Math.max(1, Math.min(width, (int) Math.ceil((double) w / cellWidth)));
                return new Size(
                        columns,
                        Math.clamp(
                                (int) Math.ceil((double) h / w * columns * cellWidth / cellHeight),
                                1,
                                1000));
            } finally {
                reader.dispose();
            }
        }
    }

    public void show(Path file, int row, int columns, int rows, int cropRows, int totalRows)
            throws IOException {
        int id = nextId++;
        ids.add(id);
        try (var stream = ImageIO.createImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image: " + file);
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L) {
                    throw new IOException("Image exceeds 20 megapixels");
                }
                BufferedImage image = reader.read(0);
                int y = Math.min(image.getHeight() - 1, cropRows * image.getHeight() / totalRows);
                int height =
                        Math.max(
                                1,
                                Math.min(
                                        image.getHeight() - y,
                                        rows * image.getHeight() / totalRows));
                image = image.getSubimage(0, y, image.getWidth(), height);
                var png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                String data = Base64.getEncoder().encodeToString(png.toByteArray());
                backend.writeRaw("\u001b[" + (row + 1) + ";1H");
                for (int offset = 0; offset < data.length(); offset += 4096) {
                    boolean more = offset + 4096 < data.length();
                    backend.writeRaw(
                            "\u001b_G"
                                    + (offset == 0
                                            ? "a=T,t=d,f=100,q=2,i="
                                                    + id
                                                    + ",c="
                                                    + columns
                                                    + ",r="
                                                    + rows
                                                    + ","
                                            : "")
                                    + "m="
                                    + (more ? 1 : 0)
                                    + ";"
                                    + data.substring(offset, Math.min(data.length(), offset + 4096))
                                    + "\u001b\\");
                }
                backend.flush();
            } finally {
                reader.dispose();
            }
        }
    }

    public void clear() throws IOException {
        for (int id : ids) {
            backend.writeRaw("\u001b_Ga=d,d=I,i=" + id + ",q=2\u001b\\");
        }
        ids.clear();
        backend.flush();
    }

    @Override
    public void close() throws IOException {
        clear();
    }
}

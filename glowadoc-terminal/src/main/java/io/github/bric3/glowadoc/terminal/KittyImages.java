// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.terminal;

import dev.tamboui.terminal.Backend;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * Session-owned image data and viewport placements.
 */
public final class KittyImages implements AutoCloseable {
    private final Backend backend;
    private final long budget;
    private int nextId = 1 + new SecureRandom().nextInt(1_000_000_000);

    private record ImageKey(Path path, boolean dim) {}

    private final Map<ImageKey, Image> images = new LinkedHashMap<>(16, .75f, true);
    private Map<Integer, Placement> previous = Map.of();
    private long resident;
    private boolean refresh;

    public void refreshPlacements() {
        refresh = true;
    }

    public void invalidate(int id) {
        var iterator = images.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().id() == id) {
                resident -= entry.getValue().bytes();
                iterator.remove();
                refresh = true;
            }
        }
    }

    public record Size(int columns, int rows) {}

    public record Placement(
            int id,
            Path path,
            int row,
            int column,
            int columns,
            int rows,
            int cropRows,
            int totalRows,
            boolean dim
    ) {
        public Placement(int id, Path path, int row, int column, int columns, int rows, int cropRows, int totalRows) {
            this(id, path, row, column, columns, rows, cropRows, totalRows, false);
        }

        private ImageKey key() {
            return new ImageKey(path, dim);
        }
    }

    private record Image(int id, int width, int height) {
        long bytes() {
            return (long) width * height * 4;
        }
    }

    public KittyImages(Backend backend) {
        this(backend, 128L * 1024 * 1024);
    }

    KittyImages(Backend backend, long budget) {
        this.backend = backend;
        this.budget = budget;
    }

    public static Size measure(Path file, int width, int cellWidth, int cellHeight) throws IOException {
        try (var stream = ImageIO.createImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                checkDimensions(w, h);
                int columns = Math.max(1, Math.min(width, (int) Math.ceil((double) w / cellWidth)));
                return new Size(
                        columns,
                        Math.clamp((int) Math.ceil((double) h / w * columns * cellWidth / cellHeight), 1, 1000)
                );
            } finally {
                reader.dispose();
            }
        }
    }

    public void render(List<Placement> placements) throws IOException {
        var desired = new HashMap<Integer, Placement>();
        for (var placement : placements) {
            desired.put(placement.id(), placement);
        }
        if (desired.equals(previous) && !refresh) {
            return;
        }
        backend.writeRaw("7");
        try {
            for (var old : previous.values()) {
                var replacement = desired.get(old.id());
                if (replacement == null || !replacement.key().equals(old.key())) {
                    var image = images.get(old.key());
                    if (image != null) {
                        command("a=d,d=i,i=" + image.id() + ",p=" + old.id() + ",q=2");
                    }
                }
            }
            for (var placement : placements) {
                if (placement.equals(previous.get(placement.id())) && !refresh) {
                    continue;
                }
                Image image = images.get(placement.key());
                if (image == null) {
                    image = upload(placement.key());
                    images.put(placement.key(), image);
                    resident += image.bytes();
                }
                int y =
                        (int) Math.min(
                                image.height() - 1L,
                                (long) placement.cropRows() * image.height() / placement.totalRows()
                );
                int end = (int) Math.min(
                        image.height(),
                        (long) (placement.cropRows() + placement.rows()) * image.height() / placement.totalRows()
                );
                backend.writeRaw("[" + (placement.row() + 1) + ";" + (placement.column() + 1) + "H");
                command(
                        "a=p,i="
                        + image.id()
                        + ",p="
                        + placement.id()
                        + ",q=1,C=1,x=0,y="
                        + y
                        + ",w="
                        + image.width()
                        + ",h="
                        + Math.max(1, end - y)
                        + ",c="
                        + placement.columns()
                        + ",r="
                        + placement.rows()
                );
            }
            previous = Map.copyOf(desired);
            refresh = false;
            Set<ImageKey> visible = placements.stream().map(Placement::key).collect(Collectors.toSet());
            var iterator = images.entrySet().iterator();
            while (resident > budget && iterator.hasNext()) {
                var entry = iterator.next();
                // Visible images remain resident; the budget bounds retained off-screen data.
                if (visible.contains(entry.getKey())) {
                    continue;
                }
                command("a=d,d=I,i=" + entry.getValue().id() + ",q=2");
                resident -= entry.getValue().bytes();
                iterator.remove();
            }
        } finally {
            backend.writeRaw("8");
            backend.flush();
        }
    }

    private Image upload(ImageKey key) throws IOException {
        try (var stream = ImageIO.createImageInputStream(key.path().toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                var image = new Image(nextId++, reader.getWidth(0), reader.getHeight(0));
                checkDimensions(image.width(), image.height());
                if (!key.dim() && reader.getFormatName().equalsIgnoreCase("png")) {
                    try (var png = Files.newInputStream(key.path())) {
                        transmit(image.id(), png);
                    }
                } else {
                    var png = new ByteArrayOutputStream();
                    var pixels = reader.read(0);
                    if (key.dim()) {
                        var dimmed = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
                        var graphics = dimmed.createGraphics();
                        try {
                            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .65f));
                            graphics.drawImage(pixels, 0, 0, null);
                        } finally {
                            graphics.dispose();
                        }
                        pixels = dimmed;
                    }
                    ImageIO.write(pixels, "png", png);
                    try (var input = new ByteArrayInputStream(png.toByteArray())) {
                        transmit(image.id(), input);
                    }
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private void transmit(int id, InputStream input) throws IOException {
        byte[] chunk = input.readNBytes(3072);
        boolean first = true;
        do {
            byte[] next = input.readNBytes(3072);
            backend.writeRaw(
                    "_G"
                    + (first ? "a=t,t=d,f=100,q=2,i=" + id + "," : "")
                    + "m="
                    + (next.length == 0 ? 0 : 1)
                    + ";"
                    + Base64.getEncoder().encodeToString(chunk)
                    + "\\"
            );
            first = false;
            chunk = next;
        } while (chunk.length > 0);
    }

    private static void checkDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || (long) width * height > 20_000_000L) {
            throw new IOException("Image exceeds 20 megapixels");
        }
    }

    private void command(String controls) throws IOException {
        backend.writeRaw("_G" + controls + ";\\");
    }

    @Override
    public void close() throws IOException {
        for (var image : images.values()) {
            command("a=d,d=I,i=" + image.id() + ",q=2");
        }
        images.clear();
        previous = Map.of();
        resident = 0;
        backend.flush();
    }
}

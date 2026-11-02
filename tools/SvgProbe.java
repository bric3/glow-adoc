// SPDX-License-Identifier: MPL-2.0
//JAVA 27
//DEPS com.github.weisj:jsvg:2.2.0
package io.github.bric3.glowadoc.tools;

import com.github.weisj.jsvg.parser.DocumentLimits;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;

/**
 * Headless, opt-in SVG evaluation; never loads SVG support into the application.
 */
public class SvgProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        long start = System.nanoTime();
        String prefix = "<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'>";
        var image = render(prefix + "<rect width='10' height='20' fill='#ff0000' opacity='.5'/></svg>");
        check(image.getRGB(1, 1) >>> 24 == 128, "Preserve alpha");
        check((image.getRGB(1, 1) & 0xffffff) == 0xff0000, "Preserve colour");
        check(image.getRGB(15, 1) == 0, "Preserve transparent canvas");
        System.out.printf(Locale.ROOT, "first-load-and-render-ms,%.1f%n", (System.nanoTime() - start) / 1_000_000.0);

        Path secret = Files.createTempFile("svg-probe-", ".png");
        try {
            var embedded = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            embedded.setRGB(1, 1, 0xffff0000);
            ImageIO.write(embedded, "png", secret.toFile());
            var external = render(prefix + "<image href='" + secret.toUri() + "' width='20' height='20'/></svg>");
            check(external.getRGB(1, 1) == 0, "Reject external images");
            String entity =
                    "<!DOCTYPE svg [<!ENTITY xxe SYSTEM '"
                    + secret.toUri()
                    + "'>]>"
                    + prefix
                    + "<text>&xxe;</text></svg>";
            check(load(entity) == null, "Reject external entities");
        } finally {
            Files.deleteIfExists(secret);
        }
        var html = render(
                prefix
                + "<foreignObject width='20' height='20'><div xmlns='http://www.w3.org/1999/xhtml' style='background:red;width:20px;height:20px'>HTML</div></foreignObject></svg>"
        );
        System.out.println("foreign-object-painted," + (html.getRGB(1, 1) != 0));
        Files.createDirectories(Path.of("build/review/svg"));
        for (String input : args) {
            start = System.nanoTime();
            Path path = Path.of(input);
            var rendered = render(Files.readString(path));
            ImageIO.write(rendered, "png", Path.of("build/review/svg", path.getFileName() + ".png").toFile());
            System.out.printf(
                    Locale.ROOT,
                    "%s,%dx%d,%.1f%n",
                    path.getFileName(),
                    rendered.getWidth(),
                    rendered.getHeight(),
                    (System.nanoTime() - start) / 1_000_000.0
            );
        }
    }

    private static SVGDocument load(String text) {
        var xml = XMLInputFactory.newFactory();
        xml.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xml.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var context = LoaderContext
            .builder()
            .externalResourcePolicy(ResourcePolicy.DENY_ALL)
            .documentLimits(new DocumentLimits(30, 15, 2000))
            .build();
        return new SVGLoader()
            .load(() -> xml.createXMLEventReader(new StringReader(text)), URI.create("file:/svg-probe.svg"), context);
    }

    private static BufferedImage render(String text) {
        var document = load(text);
        check(document != null, "Parse SVG");
        int width = (int) Math.ceil(document.size().width);
        int height = (int) Math.ceil(document.size().height);
        check(width > 0 && height > 0 && (long) width * height <= 16_000_000, "Bound image dimensions");
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            document.render(null, graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

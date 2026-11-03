// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.asciidoctor;

import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.asciidoctor.converter.ConverterFor;
import org.asciidoctor.converter.StringConverter;
import org.jsoup.parser.Parser;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A private XML vocabulary preserves inline boundaries during Asciidoctor substitutions.
 */
@ConverterFor("glow-terminal")
public final class TerminalConverter extends StringConverter {
    public TerminalConverter(String backend, Map<String, Object> options) {
        super(backend, options);
    }

    @Override
    public String convert(ContentNode node, String transform, Map<Object, Object> options) {
        String name = transform == null ? node.getNodeName() : transform;
        if (node instanceof PhraseNode phrase) {
            String text = phrase.getText() == null ? "" : phrase.getText();
            return switch (name) {
                case "inline_quoted" -> {
                    String quoted = tag(
                            switch (phrase.getType()) {
                                case "strong" -> "strong";
                                case "emphasis" -> "em";
                                case "monospaced" -> "code";
                                case "superscript" -> "sup";
                                default -> "span";
                            },
                            text
                    );
                    yield phrase.hasRole("line-through") ? tag("strike", quoted) : quoted;
                }
                case "inline_anchor" -> {
                    if (text.isEmpty() && "xref".equals(phrase.getType())) {
                        String id =
                                String.valueOf(node.getAttribute("refid", phrase.getTarget().replaceFirst("^#", "")));
                        Object reference = node.getDocument().getCatalog().getRefs().get(id);
                        if (reference instanceof StructuralNode target) {
                            text = target.getTitle();
                        }
                    }
                    yield "<link target=\""
                            + escape(phrase.getTarget())
                            + "\">"
                            + (text == null || text.isEmpty() ? escape(phrase.getTarget()) : text)
                            + "</link>";
                }
                case "inline_image" -> {
                    // Inline targets have already passed through special-character substitution.
                    String target = Parser.unescapeEntities(phrase.getTarget(), true);
                    yield "<inline-image target=\""
                            + escape(imageTarget(node, target))
                            + "\">"
                            + imageText(node)
                            + "</inline-image>";
                }
                case "inline_break" -> text + "<br/>";
                case "inline_callout" -> "<callout number=\"" + escape(text) + "\"/>";
                case "inline_footnote" -> "[" + text + "]";
                default -> text;
            };
        }
        if (node instanceof org.asciidoctor.ast.Document doc) {
            return "<document>"
                    + (doc.getDoctitle() == null ? "" : tag("heading", doc.getDoctitle()))
                    + doc.getContent()
                    + "</document>";
        }
        if (node instanceof Section section) {
            return "<heading id=\""
                    + escape(section.getId())
                    + "\">"
                    + section.getTitle()
                    + "</heading>"
                    + section.getContent();
        }
        if (node instanceof org.asciidoctor.ast.List list) {
            var out = new StringBuilder("<list>");
            int index = 1;
            for (var item : list.getItems()) {
                String prefix;
                if (name.equals("colist")) {
                    prefix = "<callout number=\"" + index++ + "\"/> ";
                } else {
                    prefix = name.equals("olist") ? index++ + ". " : "- ";
                }
                out
                    .append("<item>")
                    .append(prefix)
                    .append(((ListItem) item).getText())
                    .append(item.getContent())
                    .append("</item>");
            }
            return out.append("</list>").toString();
        }
        if (node instanceof DescriptionList list) {
            var out = new StringBuilder("<list>");
            for (var entry : list.getItems()) {
                out.append("<item><strong>");
                for (var term : entry.getTerms()) {
                    out.append(term.getText()).append(" ");
                }
                out.append("</strong> ");
                if (entry.getDescription() != null) {
                    out.append(entry.getDescription().getText()).append(entry.getDescription().getContent());
                }
                out.append("</item>");
            }
            return out.append("</list>").toString();
        }
        if (node instanceof Table table) {
            var out = new StringBuilder(
                    "<table frame=\""
                    + escape(String.valueOf(table.getAttribute(
                            "frame",
                            table.getDocument().getAttribute("table-frame", "all"),
                            false
                    )))
                    + "\" grid=\""
                    + escape(String.valueOf(table.getAttribute(
                            "grid",
                            table.getDocument().getAttribute("table-grid", "all"),
                            false
                    )))
                    + "\">"
                    + title(table)
            );
            var header = table.getHeader();
            var footer = table.getFooter();
            for (var rows : List.of(header, table.getBody(), footer)) {
                for (var row : rows) {
                    out.append(
                            rows == header ? "<row variant=\"header\">" : rows == footer
                            ? "<row variant=\"footer\">"
                            : "<row>"
                    );
                    for (var cell : row.getCells()) {
                        out
                            .append("<cell colspan=\"")
                            .append(Math.max(1, cell.getColspan()))
                            .append("\" rowspan=\"")
                            .append(Math.max(1, cell.getRowspan()))
                            .append("\" alignment=\"")
                            .append(cell.getHorizontalAlignment().toString().toLowerCase())
                            .append("\" weight=\"")
                            .append(cell.getColumn().getWidth())
                            .append("\">")
                            .append(
                                    cell.getInnerDocument() == null ? cell.getText() : cell
                                        .getInnerDocument()
                                        .getContent()
                            )
                            .append("</cell>");
                    }
                    out.append("</row>");
                }
            }
            return out.append("</table>").toString();
        }
        if (node instanceof StructuralNode block) {
            if (name.equals("image")) {
                return "<image target=\""
                        + escape(imageTarget(node, String.valueOf(node.getAttribute("target", ""))))
                        + "\">"
                        + imageText(node)
                        + "</image>";
            }
            if (name.equals("listing") || name.equals("literal")) {
                String language = String.valueOf(node.getAttribute("language", ""));
                String style = block.getStyle();
                boolean diagram = "plantuml".equals(style) || "mermaid".equals(style) || "ditaa".equals(style);
                return "<"
                        + (diagram ? "diagram" : "source")
                        + " language=\""
                        + escape(diagram ? style : language)
                        + "\">"
                        + title(block)
                        + sourceContent(block, diagram)
                        + "</"
                        + (diagram ? "diagram" : "source")
                        + ">";
            }
            String content = String.valueOf(block.getContent());
            return switch (name) {
                case "paragraph" -> tag("paragraph", content);
                case "admonition", "quote", "verse", "example", "sidebar", "open" -> {
                    String kind = block.isOption("collapsible") ? "details" : name;
                    yield "<"
                            + kind
                            + " variant=\""
                            + escape(
                                    name.equals("admonition")
                                    ? String.valueOf(node.getAttribute("name", "note")).toLowerCase(Locale.ROOT)
                                    : block.getStyle()
                    )
                            + "\" open=\""
                            + block.isOption("open")
                            + "\">"
                            + title(block)
                            + content
                            + "</"
                            + kind
                            + ">";
                }
                case "pass" -> HtmlContent.convert(content);
                default -> content;
            };
        }
        return "";
    }

    private static String imageText(ContentNode node) {
        String alt = escape(String.valueOf(node.getAttribute("alt", "image")));
        String link = String.valueOf(node.getAttribute("link", ""));
        return link.isEmpty() ? alt : "<link target=\"" + escape(link) + "\">" + alt + "</link>";
    }

    private static String imageTarget(ContentNode node, String target) {
        String directory = String.valueOf(node.getAttribute("imagesdir", ""));
        if (directory.isEmpty()
                || target.startsWith("/")
                || target.startsWith("\\")
                || target.matches("[A-Za-z][A-Za-z0-9+.-]*:.*")) {
            return target;
        }
        // Keep resolution local; imageUri may embed files when a document enables data-uri.
        return directory.replaceFirst("/+$", "") + "/" + target;
    }

    private static String tag(String name, String content) {
        return "<" + name + ">" + content + "</" + name + ">";
    }

    private static String title(StructuralNode block) {
        return block.getTitle() == null ? "" : tag("title", block.getTitle());
    }

    private static String sourceContent(StructuralNode block, boolean diagram) {
        if (!diagram && block.isSubstitutionEnabled("callouts")) {
            // Escape code for the private XML vocabulary before substituting callout elements.
            boolean addEscaping = !block.isSubstitutionEnabled("specialcharacters");
            if (addEscaping) {
                block.prependSubstitution("specialcharacters");
            }
            try {
                return String.valueOf(block.getContent());
            } finally {
                if (addEscaping) {
                    block.removeSubstitution("specialcharacters");
                }
            }
        }
        return escape(
                block instanceof org.asciidoctor.ast.Block source ? source.getSource() : String.valueOf(block.getContent()
                )
        );
    }

    private static String escape(String text) {
        return text == null
                ? ""
                : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

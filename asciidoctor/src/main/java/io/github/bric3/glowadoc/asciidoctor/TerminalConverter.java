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

import java.util.Map;

/** A private XML vocabulary preserves inline boundaries during Asciidoctor substitutions. */
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
                case "inline_quoted" ->
                        tag(
                                switch (phrase.getType()) {
                                    case "strong" -> "strong";
                                    case "emphasis" -> "em";
                                    case "monospaced" -> "code";
                                    default -> "span";
                                },
                                text);
                case "inline_anchor" -> {
                    if (text.isEmpty() && "xref".equals(phrase.getType())) {
                        String id =
                                String.valueOf(
                                        node.getAttribute(
                                                "refid",
                                                phrase.getTarget().replaceFirst("^#", "")));
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
                case "inline_image" -> escape("[image: " + node.getAttribute("alt", "image") + "]");
                case "inline_break" -> text + "\n";
                case "inline_callout" -> escape("(" + text + ")");
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
                out.append("<item>")
                        .append(name.equals("olist") ? index++ + ". " : "- ")
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
                    out.append(entry.getDescription().getText())
                            .append(entry.getDescription().getContent());
                }
                out.append("</item>");
            }
            return out.append("</list>").toString();
        }
        if (node instanceof Table table) {
            var out = new StringBuilder("<table>");
            for (var rows :
                    java.util.List.of(table.getHeader(), table.getBody(), table.getFooter())) {
                for (var row : rows) {
                    out.append("<row>");
                    for (var cell : row.getCells()) {
                        out.append(tag("cell", cell.getText()));
                    }
                    out.append("</row>");
                }
            }
            return out.append("</table>").toString();
        }
        if (node instanceof StructuralNode block) {
            if (name.equals("image")) {
                return "<image target=\""
                        + escape(String.valueOf(node.getAttribute("target", "")))
                        + "\">"
                        + escape(String.valueOf(node.getAttribute("alt", "image")))
                        + "</image>";
            }
            if (name.equals("listing") || name.equals("literal")) {
                String language = String.valueOf(node.getAttribute("language", ""));
                String style = block.getStyle();
                boolean diagram = "plantuml".equals(style) || "mermaid".equals(style);
                return "<"
                        + (diagram ? "diagram" : "source")
                        + " language=\""
                        + escape(diagram ? style : language)
                        + "\">"
                        + escape(
                                block instanceof org.asciidoctor.ast.Block source
                                        ? source.getSource()
                                        : String.valueOf(block.getContent()))
                        + "</"
                        + (diagram ? "diagram" : "source")
                        + ">";
            }
            String content = String.valueOf(block.getContent());
            return switch (name) {
                case "paragraph" -> tag("paragraph", content);
                case "admonition" ->
                        tag(
                                "admonition",
                                tag("strong", escape(block.getStyle().toUpperCase()) + ": ")
                                        + content);
                case "quote", "verse" -> tag("quote", content);
                case "pass" -> tag("source", escape(content));
                default -> content;
            };
        }
        return "";
    }

    private static String tag(String name, String content) {
        return "<" + name + ">" + content + "</" + name + ">";
    }

    private static String escape(String text) {
        return text == null
                ? ""
                : text.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }
}

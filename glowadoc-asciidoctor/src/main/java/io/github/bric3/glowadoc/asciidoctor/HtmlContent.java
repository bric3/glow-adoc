// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.asciidoctor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import java.util.Set;
import java.util.regex.Pattern;

final class HtmlContent {
    private static final Pattern SOFT_BREAK = Pattern.compile("[ \\t]*\\r?\\n[ \\t]*");

    private HtmlContent() {
    }

    static String convert(String html) {
        var body = Jsoup.parseBodyFragment(html).body();
        for (Element details : body.select("details[open]")) {
            details.attr("open", "true");
        }
        return nodes(body);
    }

    static String normalise(String xml) {
        return nodes(Jsoup.parse(xml, "", Parser.xmlParser()));
    }

    private static String nodes(Node parent) {
        return nodes(parent, false);
    }

    private static String nodes(Node parent, boolean inheritedStrike) {
        var out = new StringBuilder();
        for (Node node : parent.childNodes()) {
            if (node instanceof TextNode text) {
                if (inheritedStrike) {
                    out.append("<strike>");
                }
                boolean literal =
                        parent instanceof Element element && element.closest("source, verse, diagram, pre, code") != null;
                String value = literal ? text.getWholeText() : SOFT_BREAK.matcher(text.getWholeText()).replaceAll(" ");
                if (!literal && text.previousSibling() instanceof Element previous && previous
                    .normalName()
                    .equals("br")) {
                    value = value.stripLeading();
                }
                out.append(escape(value));
                if (inheritedStrike) {
                    out.append("</strike>");
                }
                continue;
            }
            if (!(node instanceof Element element)) {
                continue;
            }
            String name = element.normalName();
            if (Set.of("script", "style", "iframe", "object", "template").contains(name)) {
                continue;
            }
            String tag = switch (name) {
                case "b" -> "strong";
                case "i" -> "em";
                case "s", "del" -> "strike";
                case "p", "div" -> "paragraph";
                case "h1", "h2", "h3", "h4", "h5", "h6" -> "heading";
                case "blockquote" -> "quote";
                case "pre" -> "source";
                case "ul", "ol" -> "list";
                case "li" -> "item";
                case "a" -> "link";
                case "tr" -> "row";
                case "td", "th" -> "cell";
                default -> name;
            };
            if (name.equals("inline-image")) {
                Element parentElement = element.parent();
                if (parentElement != null
                        && parentElement.normalName().equals("paragraph")
                        && parentElement.ownText().isBlank()
                        && parentElement
                            .children()
                            .stream()
                            .allMatch(child -> child.normalName().equals("inline-image"))) {
                    tag = "image";
                } else {
                    out.append("[image: ").append(nodes(element)).append("]");
                    continue;
                }
            }
            if (name.equals("br")) {
                out.append("<br/>");
                out.append(nodes(element));
                continue;
            }
            if (name.equals("img")) {
                out.append(escape("[image: " + element.attr("alt") + "]"));
                out.append(nodes(element));
                continue;
            }
            if (name.equals("sup")) {
                out.append("<span>[").append(nodes(element)).append("]</span>");
                continue;
            }
            if (!Set
                .of(
                        "document",
                        "title",
                        "callout",
                        "strong",
                        "em",
                        "strike",
                        "span",
                        "code",
                        "paragraph",
                        "heading",
                        "quote",
                        "source",
                        "list",
                        "item",
                        "link",
                        "table",
                        "row",
                        "cell",
                        "details",
                        "admonition",
                        "verse",
                        "example",
                        "sidebar",
                        "open",
                        "image",
                        "diagram"
                )
                .contains(tag)) {
                if (!name.equals("summary")) {
                    out.append(nodes(element));
                }
                continue;
            }
            out.append("<").append(tag);
            for (String attr :
                    Set.of(
                            "id",
                            "title",
                            "variant",
                            "alignment",
                            "weight",
                            "colspan",
                            "level",
                            "rowspan",
                            "language",
                            "target",
                            "number",
                            "frame",
                            "grid"
            )) {
                String value = element.attr(attr);
                if (attr.equals("target") && name.equals("a")) {
                    value = element.attr("href");
                }
                if (attr.equals("variant") && name.equals("th")) {
                    value = "header";
                }
                if (attr.equals("variant") && name.equals("tr") && element.parent() != null) {
                    if (element.parent().normalName().equals("thead")) {
                        value = "header";
                    }
                    if (element.parent().normalName().equals("tfoot")) {
                        value = "footer";
                    }
                }
                if (attr.equals("alignment") && element.hasAttr("align")) {
                    value = element.attr("align");
                }
                if (attr.equals("title") && name.equals("details")) {
                    Element summary =
                            element
                        .children()
                        .stream()
                        .filter(e -> e.normalName().equals("summary"))
                        .findFirst()
                        .orElse(null);
                    value = summary == null ? (value.isEmpty() ? "Details" : value) : summary.text();
                }
                if (!value.isEmpty()) {
                    out.append(" ").append(attr).append("=\"").append(escape(value)).append("\"");
                }
            }
            if (tag.equals("details")) {
                boolean open = element.hasAttr("open") && !element.attr("open").equals("false");
                out.append(" open=\"").append(open).append("\"");
            }
            out.append(">");
            if (name.equals("details")) {
                for (Element child : element.children()) {
                    if (child.normalName().equals("summary")) {
                        out.append("<title>").append(nodes(child)).append("</title>");
                    }
                }
            }
            if (name.equals("li")) {
                out.append(
                        element.parent() != null && element.parent().normalName().equals("ol")
                        ? (element.elementSiblingIndex() + 1) + ". "
                        : "- "
                );
            }
            boolean strike = inheritedStrike
                    || element.attr("style").matches("(?is).*text-decoration(?:-line)?\\s*:[^;]*line-through.*")
                    || element.classNames().contains("line-through");
            if (tag.equals("source") && name.equals("pre")) {
                out.append(escape(element.wholeText()));
            } else {
                out.append(nodes(element, strike));
            }
            out.append("</").append(tag).append(">");
        }
        return out.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

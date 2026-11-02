package guru.interlis.documentation;

import java.util.*;

/** Standalone inert HTML. All model text is escaped; resources and scripts are disabled. */
public final class HtmlDocumentationRenderer {
    public String render(DocumentationDocument d) {
        var b =
                new StringBuilder(
                        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta"
                                + " http-equiv=\"Content-Security-Policy\" content=\"default-src"
                                + " 'none'; style-src 'unsafe-inline'\"><title>"
                                + x(DocumentationLabels.get(d.options(), "catalog"))
                                + "</title><style>body{font:16px"
                                + " system-ui;margin:2em;color:#202020;background:#fff}table{border-collapse:collapse;width:100%;table-layout:fixed}td,th{border:1px"
                                + " solid"
                                + " #777;padding:.4em;overflow-wrap:anywhere;text-align:left}h2{break-before:auto}.abstract{font-style:italic}p,td{white-space:pre-wrap}</style></head><body><h1>"
                                + x(DocumentationLabels.get(d.options(), "catalog"))
                                + "</h1><p>"
                                + x(DocumentationText.provenance(d))
                                + "</p>");
        for (var s : d.sections()) {
            b.append("<section id=\"")
                    .append(DocumentationText.anchor(s.id()))
                    .append("\"><h2")
                    .append(s.abstractType() ? " class=\"abstract\"" : "")
                    .append('>')
                    .append(x(s.name()))
                    .append(s.abstractType() ? " «ABSTRACT»" : "")
                    .append("</h2><p>")
                    .append(x(s.kind() + " " + s.type()))
                    .append("</p><p>")
                    .append(x(s.description()))
                    .append("</p>");
            if (!s.base().isEmpty())
                b.append("<p>")
                        .append(x(DocumentationLabels.get(d.options(), "base") + ": " + s.base()))
                        .append("</p>");
            table(b, d, s.members());
            if (!s.inherited().isEmpty()) {
                b.append("<h3>")
                        .append(x(DocumentationLabels.get(d.options(), "inherited")))
                        .append("</h3>");
                table(b, d, s.inherited());
            }
            if (!s.constraints().isEmpty()) {
                b.append("<h3>")
                        .append(x(DocumentationLabels.get(d.options(), "constraints")))
                        .append("</h3><ul>");
                for (var c : s.constraints()) b.append("<li>").append(x(c)).append("</li>");
                b.append("</ul>");
            }
            b.append("</section>");
            DocumentationText.checkSize(b.length());
        }
        return b.append("</body></html>").toString();
    }

    private static String x(String v) {
        return DocumentationText.xml(v);
    }

    private static void table(
            StringBuilder b, DocumentationDocument d, List<DocumentationDocument.Member> members) {
        if (members.isEmpty()) return;
        b.append("<table><thead><tr>");
        for (var h : DocumentationText.headers(d.options()))
            b.append("<th scope=\"col\">").append(x(h)).append("</th>");
        b.append("</tr></thead><tbody>");
        for (var m : members) {
            b.append("<tr>");
            for (var c : DocumentationText.cells(m)) b.append("<td>").append(x(c)).append("</td>");
            b.append("</tr>");
            DocumentationText.checkSize(b.length());
        }
        b.append("</tbody></table>");
    }
}

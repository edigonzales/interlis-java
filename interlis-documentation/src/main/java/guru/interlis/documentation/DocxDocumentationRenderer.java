package guru.interlis.documentation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Minimal standards-based OOXML package with real headings, tables, styles and relationships. Uses
 * only JDK ZIP; no fonts, external targets, macros, templates or network references.
 */
public final class DocxDocumentationRenderer {
    public byte[] render(DocumentationDocument d) {
        var body = new StringBuilder();
        paragraph(body, DocumentationLabels.get(d.options(), "catalog"), "Title", false);
        paragraph(body, DocumentationText.provenance(d), "Normal", false);
        for (var s : d.sections()) {
            paragraph(
                    body,
                    s.name() + (s.abstractType() ? " «ABSTRACT»" : ""),
                    "Heading1",
                    s.abstractType());
            paragraph(body, s.kind() + " " + s.type(), "Normal", false);
            if (!s.base().isEmpty())
                paragraph(
                        body,
                        DocumentationLabels.get(d.options(), "base") + ": " + s.base(),
                        "Normal",
                        false);
            paragraph(body, s.description(), "Normal", false);
            table(body, d, s.members());
            if (!s.inherited().isEmpty()) {
                paragraph(
                        body, DocumentationLabels.get(d.options(), "inherited"), "Heading2", false);
                table(body, d, s.inherited());
            }
            if (!s.constraints().isEmpty()) {
                paragraph(
                        body,
                        DocumentationLabels.get(d.options(), "constraints"),
                        "Heading2",
                        false);
                for (var c : s.constraints()) paragraph(body, c, "Normal", false);
            }
            DocumentationText.checkSize(body.length());
        }
        String document =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document"
                    + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                        + body
                        + "<w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\""
                        + " w:orient=\"landscape\"/><w:pgMar w:top=\"720\" w:right=\"720\""
                        + " w:bottom=\"720\" w:left=\"720\"/></w:sectPr></w:body></w:document>";
        var parts = new LinkedHashMap<String, String>();
        parts.put(
                "[Content_Types].xml",
                "<?xml version=\"1.0\"?><Types"
                    + " xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
                    + " Extension=\"rels\""
                    + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default"
                    + " Extension=\"xml\" ContentType=\"application/xml\"/><Override"
                    + " PartName=\"/word/document.xml\""
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override"
                    + " PartName=\"/word/styles.xml\""
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/></Types>");
        parts.put(
                "_rels/.rels",
                "<?xml version=\"1.0\"?><Relationships"
                    + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
                    + " Id=\"rId1\""
                    + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
                    + " Target=\"word/document.xml\"/></Relationships>");
        parts.put(
                "word/_rels/document.xml.rels",
                "<?xml version=\"1.0\"?><Relationships"
                    + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
                    + " Id=\"rId1\""
                    + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\""
                    + " Target=\"styles.xml\"/></Relationships>");
        parts.put(
                "word/styles.xml",
                "<?xml version=\"1.0\"?><w:styles"
                    + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:style"
                    + " w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name"
                    + " w:val=\"Normal\"/><w:rPr><w:sz w:val=\"20\"/></w:rPr></w:style>"
                        + style("Title", 36)
                        + style("Heading1", 28)
                        + style("Heading2", 24)
                        + "</w:styles>");
        parts.put("word/document.xml", document);
        try (var bytes = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var part : parts.entrySet()) {
                var entry = new ZipEntry(part.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            DocumentationText.checkSize(bytes.size());
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String style(String id, int size) {
        return "<w:style w:type=\"paragraph\" w:styleId=\""
                + id
                + "\"><w:name w:val=\""
                + id
                + "\"/><w:basedOn w:val=\"Normal\"/><w:next"
                + " w:val=\"Normal\"/><w:pPr><w:keepNext/><w:outlineLvl w:val=\""
                + (id.equals("Heading2") ? 1 : 0)
                + "\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\""
                + size
                + "\"/></w:rPr></w:style>";
    }

    private static void paragraph(StringBuilder b, String text, String style, boolean italic) {
        b.append("<w:p><w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr><w:r>");
        if (italic) b.append("<w:rPr><w:i/></w:rPr>");
        var lines = text.replace("\r", "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) b.append("<w:br/>");
            b.append("<w:t xml:space=\"preserve\">")
                    .append(DocumentationText.xml(lines[i]))
                    .append("</w:t>");
        }
        b.append("</w:r></w:p>");
    }

    private static void table(
            StringBuilder b, DocumentationDocument d, List<DocumentationDocument.Member> members) {
        if (members.isEmpty()) return;
        b.append("<w:tbl><w:tblPr><w:tblW w:w=\"15398\" w:type=\"dxa\"/><w:tblBorders>");
        for (String edge : List.of("top", "left", "bottom", "right", "insideH", "insideV"))
            b.append("<w:")
                    .append(edge)
                    .append(" w:val=\"single\" w:sz=\"4\" w:color=\"777777\"/>");
        b.append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/></w:tblPr><w:tblGrid>");
        for (int i = 0; i < 6; i++) b.append("<w:gridCol w:w=\"2566\"/>");
        b.append("</w:tblGrid>");
        row(b, DocumentationText.headers(d.options()), true);
        for (var m : members) {
            row(b, DocumentationText.cells(m), false);
            DocumentationText.checkSize(b.length());
        }
        b.append("</w:tbl>");
    }

    private static void row(StringBuilder b, List<String> cells, boolean header) {
        b.append("<w:tr>");
        if (header) b.append("<w:trPr><w:tblHeader/></w:trPr>");
        for (var c : cells) {
            b.append("<w:tc><w:tcPr><w:tcW w:w=\"2566\" w:type=\"dxa\"/></w:tcPr>");
            paragraph(b, c, "Normal", false);
            b.append("</w:tc>");
        }
        b.append("</w:tr>");
    }
}

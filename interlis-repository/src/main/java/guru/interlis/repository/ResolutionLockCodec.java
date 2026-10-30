package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import org.w3c.dom.Element;

/** Deterministic UTF-8 XML lock format v1. No external entities, IO or implicit version upgrades. */
public final class ResolutionLockCodec {
    public String encode(ResolutionLock lock) {
        Objects.requireNonNull(lock, "lock");
        try {
            var out = new StringWriter(); var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("interlis-resolution-lock"); xml.writeAttribute("version", "1");
            var indexes = new TreeMap<String, ContentDigest>();
            lock.indexes().forEach((id, digest) -> indexes.put(id.value(), digest));
            for (var e : indexes.entrySet()) {
                xml.writeEmptyElement("index"); xml.writeAttribute("source", e.getKey()); xml.writeAttribute("sha256", e.getValue().value());
            }
            for (var group : new TreeMap<>(lock.models()).values()) for (var pin : group) {
                xml.writeEmptyElement("model"); xml.writeAttribute("name", pin.modelName()); xml.writeAttribute("version", pin.modelVersion());
                xml.writeAttribute("source", pin.source().value()); xml.writeAttribute("sha256", pin.digest().value());
            }
            xml.writeEndElement(); xml.writeEndDocument(); xml.close(); return out.toString().replace("\r", "&#xD;").replace("\n", "&#xA;").replace("\t", "&#x9;") + "\n";
        } catch (javax.xml.stream.XMLStreamException invalid) { throw new IllegalArgumentException("lock cannot be encoded", invalid); }
    }

    /** Rejects malformed/oversize locks, duplicate keys, unknown fields and unsupported versions. */
    public ResolutionLock decode(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > 8_388_608) throw new IllegalArgumentException("lock exceeds limit");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
            if (!root.getTagName().equals("interlis-resolution-lock") || !root.getAttribute("version").equals("1")) throw new IllegalArgumentException("unsupported lock format");
            attributes(root, Set.of("version"));
            var indexes = new LinkedHashMap<SourceId, ContentDigest>(); var models = new LinkedHashMap<String, java.util.List<ModelPin>>();
            for (var node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element element)) {
                    if (!node.getTextContent().isBlank()) throw new IllegalArgumentException("unexpected lock text");
                    continue;
                }
                if (element.hasChildNodes()) throw new IllegalArgumentException("lock entries must be empty");
                switch (element.getTagName()) {
                    case "index" -> {
                        attributes(element, Set.of("source", "sha256"));
                        if (indexes.putIfAbsent(new SourceId(element.getAttribute("source")), new ContentDigest("sha256", element.getAttribute("sha256"))) != null)
                            throw new IllegalArgumentException("duplicate lock index");
                    }
                    case "model" -> {
                        attributes(element, Set.of("name", "version", "source", "sha256"));
                        var pin = new ModelPin(element.getAttribute("name"), element.getAttribute("version"), new SourceId(element.getAttribute("source")), new ContentDigest("sha256", element.getAttribute("sha256")));
                        models.computeIfAbsent(pin.modelName(), ignored -> new java.util.ArrayList<>()).add(pin);
                    }
                    default -> throw new IllegalArgumentException("unknown lock element");
                }
            }
            return new ResolutionLock(models, indexes);
        } catch (IllegalArgumentException invalid) { throw invalid; }
        catch (Exception invalid) { throw new IllegalArgumentException("invalid resolution lock", invalid); }
    }
    private static void attributes(Element element, Set<String> expected) {
        var attributes = element.getAttributes();
        if (attributes.getLength() != expected.size()) throw new IllegalArgumentException("invalid lock fields");
        for (int i=0;i<attributes.getLength();i++) if (!expected.contains(attributes.item(i).getNodeName())) throw new IllegalArgumentException("unknown lock field");
    }
}

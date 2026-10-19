package guru.interlis.repository;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the standard INTERLIS {@code ilimodels.xml} repository index profile.
 *
 * <p>Model files are resolved relative to the repository base. Entries that
 * escape the base hierarchy or use a non-http(s)/file scheme are rejected.</p>
 */
public final class RepositoryIndexReader {
    private static final String MODEL_METADATA = "ModelMetadata";

    /**
     * Reads one index document.
     *
     * @param payload raw index document
     * @param base absolute repository base
     * @return immutable index
     * @throws RepositoryIndexException for malformed, incomplete or unsafe indexes
     */
    public RepositoryIndex read(IndexPayload payload, RepositoryBase base) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(base, "base");
        Document document = parse(payload.content());
        List<RepositoryModelMetadata> models = new ArrayList<>();
        NodeList all = document.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node instanceof Element element
                    && (MODEL_METADATA.equals(localName(element))
                    || localName(element).endsWith("." + MODEL_METADATA))) {
                models.add(readModel(element, base));
            }
        }
        if (models.isEmpty()) {
            throw new RepositoryIndexException(RepositoryIndexException.Reason.MISSING_METADATA,
                    "repository index contains no ModelMetadata entries", null);
        }
        return new RepositoryIndex(base, models);
    }

    private RepositoryModelMetadata readModel(Element element, RepositoryBase base) {
        String name = text(element, "Name");
        String schemaLanguage = text(element, "SchemaLanguage");
        String file = text(element, "File");
        String version = text(element, "Version");
        if (name == null || schemaLanguage == null || file == null || version == null) {
            throw new RepositoryIndexException(RepositoryIndexException.Reason.MISSING_METADATA,
                    "ModelMetadata requires Name, SchemaLanguage, File and Version", null);
        }
        URI location = resolve(base, file);
        return new RepositoryModelMetadata(name, schemaLanguage, location, version,
                Optional.ofNullable(text(element, "precursorVersion")),
                Optional.ofNullable(text(element, "md5")));
    }

    private static URI resolve(RepositoryBase base, String file) {
        URI candidate;
        try {
            candidate = URI.create(file);
        } catch (IllegalArgumentException failure) {
            throw new RepositoryIndexException(RepositoryIndexException.Reason.UNSAFE_LOCATION,
                    "invalid model file URI", failure);
        }
        if (candidate.isAbsolute()) {
            String scheme = candidate.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)
                    && !"file".equalsIgnoreCase(scheme)) {
                throw new RepositoryIndexException(RepositoryIndexException.Reason.UNSAFE_LOCATION,
                        "model file URI scheme is not allowed", null);
            }
        }
        URI resolved = base.uri().resolve(candidate).normalize();
        String baseText = base.uri().normalize().toString();
        if (!baseText.endsWith("/")) {
            baseText = baseText + "/";
        }
        if (!resolved.toString().startsWith(baseText)) {
            throw new RepositoryIndexException(RepositoryIndexException.Reason.UNSAFE_LOCATION,
                    "model file URI escapes repository base", null);
        }
        return resolved;
    }

    private static Document parse(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new RepositoryIndexException(RepositoryIndexException.Reason.MALFORMED,
                    "repository index is not well-formed XML", failure);
        }
    }

    private static String text(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(localName(element))) {
                String value = element.getTextContent();
                return value == null || value.isBlank() ? null : value.trim();
            }
        }
        return null;
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        return local == null ? element.getTagName() : local;
    }
}

package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Stores the current, atomically published content digest for each URL. */
final class DirectoryMetadataStore {
    private static final int LIMIT = 4096;
    private static final Set<String> ATTRIBUTES = Set.of("version", "source-key", "sha256", "downloaded-at");

    record Current(ContentDigest digest, Instant downloadedAt) { }

    private final Path directory;
    private final Runnable writeFailure;

    DirectoryMetadataStore(Path root, Runnable writeFailure) {
        this.directory = root.resolve("by-url");
        this.writeFailure = writeFailure;
    }

    private Path file(String key) {
        return directory.resolve(key).resolve(".current.xml");
    }

    Optional<Current> lookup(SourceId source) {
        String key = RepositoryCachePath.key(source);
        try (var input = Files.newInputStream(file(key))) {
            byte[] bytes = input.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) return Optional.empty();
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var entry = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!entry.getTagName().equals("interlis-cache") || !entry.getAttribute("version").equals("3")
                    || !entry.getAttribute("source-key").equals(key)) return Optional.empty();
            for (int i = 0; i < entry.getAttributes().getLength(); i++) {
                if (!ATTRIBUTES.contains(entry.getAttributes().item(i).getNodeName())) return Optional.empty();
            }
            for (Node child = entry.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.TEXT_NODE || !child.getTextContent().isBlank()) return Optional.empty();
            }
            return Optional.of(new Current(new ContentDigest("sha256", entry.getAttribute("sha256")),
                    Instant.parse(entry.getAttribute("downloaded-at"))));
        } catch (IOException | ParserConfigurationException | SAXException | IllegalArgumentException | DateTimeParseException failure) {
            return Optional.empty();
        }
    }

    void put(SourceId source, ContentDigest digest, Instant downloadedAt) {
        String key = RepositoryCachePath.key(source);
        Path target = file(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".metadata-", ".tmp");
            // Keys contain only portable ASCII. Digest and Instant also need no XML escaping.
            Files.writeString(temporary, """
                    <interlis-cache version="3" source-key="%s" sha256="%s" downloaded-at="%s"/>
                    """.formatted(key, digest.value(), downloadedAt));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unavailable) {
            writeFailure.run();
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* optional cache */ }
            }
        }
    }

    void remove(SourceId source) {
        try {
            Files.deleteIfExists(file(RepositoryCachePath.key(source)));
        } catch (IOException failure) {
            writeFailure.run();
        }
    }
}

package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, strict v1 metadata. Invalid/unavailable entries are misses; writes replace atomically. */
final class DirectoryMetadataStore {
    private static final int LIMIT = 65_536;
    private final Path directory;
    private final Runnable writeFailure;
    DirectoryMetadataStore(Path root, Runnable writeFailure) {
        this.directory = root.resolve("metadata-v1"); this.writeFailure = writeFailure;
    }
    private Path file(SourceId source) {
        return directory.resolve(guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digest(
                source.value().getBytes(StandardCharsets.UTF_8))) + ".xml");
    }
    Optional<RepositoryCacheMetadata> lookup(SourceId source) {
        try (var input = Files.newInputStream(file(source))) {
            byte[] bytes = input.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) return Optional.empty();
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder(); builder.setErrorHandler(new DefaultHandler());
            var root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!root.getTagName().equals("interlis-cache") || !root.getAttribute("version").equals("1")) return Optional.empty();
            var allowed = Set.of("version", "source", "response-source", "sha256", "etag", "last-modified");
            for (int i=0;i<root.getAttributes().getLength();i++)
                if (!allowed.contains(root.getAttributes().item(i).getNodeName())) return Optional.empty();
            for (Node child=root.getFirstChild();child!=null;child=child.getNextSibling())
                if (child.getNodeType()!=Node.TEXT_NODE || !child.getTextContent().isBlank()) return Optional.empty();
            if (!root.getAttribute("source").equals(source.value()) || !root.hasAttribute("response-source")) return Optional.empty();
            return Optional.of(new RepositoryCacheMetadata(source,new SourceId(root.getAttribute("response-source")),
                    new ContentDigest("sha256",root.getAttribute("sha256")),attribute(root,"etag"),attribute(root,"last-modified")));
        } catch (IOException | javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException | IllegalArgumentException failure) {
            return Optional.empty();
        }
    }
    private Optional<String> attribute(Element root,String name) { return root.hasAttribute(name)?Optional.of(root.getAttribute(name)):Optional.empty(); }
    void put(RepositoryCacheMetadata metadata) {
        Path temporary = null;
        try {
            var output = new ByteArrayOutputStream();
            var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0"); xml.writeEmptyElement("interlis-cache");
            xml.writeAttribute("version","1"); xml.writeAttribute("source",metadata.source().value());
            xml.writeAttribute("response-source",metadata.responseSource().value()); xml.writeAttribute("sha256",metadata.digest().value());
            if(metadata.etag().isPresent())xml.writeAttribute("etag",metadata.etag().orElseThrow());
            if(metadata.lastModified().isPresent())xml.writeAttribute("last-modified",metadata.lastModified().orElseThrow());
            xml.writeEndDocument();xml.close();
            if(output.size()>LIMIT) { remove(metadata.source()); return; }
            Files.createDirectories(directory); temporary=Files.createTempFile(directory,".metadata-",".tmp");
            Files.write(temporary,output.toByteArray());
            Files.move(temporary,file(metadata.source()),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | javax.xml.stream.XMLStreamException failure) { writeFailure.run(); }
        finally { if(temporary!=null)try { Files.deleteIfExists(temporary); } catch(IOException ignored) { /* optional cache */ } }
    }
    void remove(SourceId source) {
        try { Files.deleteIfExists(file(source)); } catch(IOException failure) { writeFailure.run(); }
    }
}

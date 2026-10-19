package guru.interlis.repository;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryIndexReaderTest {
    private static final RepositoryBase BASE =
            new RepositoryBase(URI.create("https://models.example/repo/"));

    private static final String INDEX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
              <DATASECTION>
                <IliRepository20.RepositoryIndex BID="b1">
                  <IliRepository20.RepositoryIndex.ModelMetadata TID="1">
                    <Name>Alpha</Name>
                    <SchemaLanguage>ili2_4</SchemaLanguage>
                    <File>models/Alpha.ili</File>
                    <Version>2024-01-01</Version>
                    <md5>abc</md5>
                  </IliRepository20.RepositoryIndex.ModelMetadata>
                  <IliRepository20.RepositoryIndex.ModelMetadata TID="2">
                    <Name>Beta</Name>
                    <SchemaLanguage>ili2_3</SchemaLanguage>
                    <File>models/nested/Beta.ili</File>
                    <Version>1</Version>
                  </IliRepository20.RepositoryIndex.ModelMetadata>
                </IliRepository20.RepositoryIndex>
              </DATASECTION>
            </TRANSFER>
            """;

    @Test
    void readsStandardModelMetadataAndResolvesRelativeFiles() {
        RepositoryIndex index = new RepositoryIndexReader().read(new IndexPayload(INDEX), BASE);
        assertEquals(2, index.models().size());
        assertEquals("Alpha", index.models().getFirst().name());
        assertEquals("2024-01-01", index.models().getFirst().version());
        assertEquals(URI.create("https://models.example/repo/models/Alpha.ili"),
                index.models().getFirst().location());
        assertEquals("abc", index.models().getFirst().md5().orElseThrow());
        assertTrue(index.models().get(1).precursorVersion().isEmpty());
    }

    @Test
    void readsOwn09And20ReferenceProfileFixtures() throws Exception {
        assertEquals("LegacyModel", readResource("/repository/index09.xml").models()
                .getFirst().name());
        assertEquals("AbstractSymbology", readResource("/repository/index20.xml").models()
                .getFirst().name());
    }

    private static RepositoryIndex readResource(String resource) throws Exception {
        try (InputStream stream = RepositoryIndexReaderTest.class.getResourceAsStream(resource)) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new RepositoryIndexReader().read(new IndexPayload(content), BASE);
        }
    }

    @Test
    void rejectsPathEscapingRepositoryBase() {
        String unsafe = INDEX.replace("models/Alpha.ili", "../../outside.ili");
        RepositoryIndexException failure = assertThrows(RepositoryIndexException.class,
                () -> new RepositoryIndexReader().read(new IndexPayload(unsafe), BASE));
        assertEquals(RepositoryIndexException.Reason.UNSAFE_LOCATION, failure.reason());
    }

    @Test
    void rejectsMissingMetadataAndMalformedXml() {
        String missing = INDEX.replace("<Version>2024-01-01</Version>", "");
        RepositoryIndexException missingFailure = assertThrows(RepositoryIndexException.class,
                () -> new RepositoryIndexReader().read(new IndexPayload(missing), BASE));
        assertEquals(RepositoryIndexException.Reason.MISSING_METADATA, missingFailure.reason());
        RepositoryIndexException malformed = assertThrows(RepositoryIndexException.class,
                () -> new RepositoryIndexReader().read(new IndexPayload("<broken>"), BASE));
        assertEquals(RepositoryIndexException.Reason.MALFORMED, malformed.reason());
    }
}

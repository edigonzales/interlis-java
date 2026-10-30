package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryClientTest {
    private static final String INDEX = """
            <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
              <DATASECTION>
                <IliRepository20.RepositoryIndex>
                  <IliRepository20.RepositoryIndex.ModelMetadata>
                    <Name>Alpha</Name>
                    <SchemaLanguage>ili2_4</SchemaLanguage>
                    <File>Alpha.ili</File>
                    <Version>1</Version>
                  </IliRepository20.RepositoryIndex.ModelMetadata>
                </IliRepository20.RepositoryIndex>
              </DATASECTION>
            </TRANSFER>
            """;

    @Test
    void returnsCandidatesWithDeterministicPriorities() {
        RepositoryClient client = new RepositoryClient(new MapLoader(Map.of(
                "https://a.example/repo/ilimodels.xml", INDEX,
                "https://b.example/repo/ilimodels.xml", INDEX)),
                new RepositoryIndexReader());
        RepositoryConfig config = new RepositoryConfig(java.util.List.of(
                new RepositorySource(new RepositoryBase(URI.create("https://a.example/repo/")),
                        new SourceLocator("https://a.example/repo/ilimodels.xml")),
                new RepositorySource(new RepositoryBase(URI.create("https://b.example/repo/")),
                        new SourceLocator("https://b.example/repo/ilimodels.xml"))),
                FetchPolicy.defaults());
        RepositoryDiscovery discovery = client.discover(new ModelQuery("Alpha",
                Optional.of("1"), Optional.empty()), config, CancellationToken.none());
        assertEquals(2, discovery.candidates().size());
        assertEquals(0, discovery.candidates().getFirst().priority());
        assertEquals(1, discovery.candidates().get(1).priority());
        assertTrue(discovery.failures().isEmpty());
    }

    @Test
    void separatesLoadFailureFromOtherCandidates() {
        RepositoryClient client = new RepositoryClient(new MapLoader(Map.of(
                "https://b.example/repo/ilimodels.xml", INDEX)),
                new RepositoryIndexReader());
        RepositoryConfig config = new RepositoryConfig(java.util.List.of(
                new RepositorySource(new RepositoryBase(URI.create("https://a.example/repo/")),
                        new SourceLocator("https://a.example/repo/ilimodels.xml")),
                new RepositorySource(new RepositoryBase(URI.create("https://b.example/repo/")),
                        new SourceLocator("https://b.example/repo/ilimodels.xml"))),
                FetchPolicy.defaults());
        RepositoryDiscovery discovery = client.discover(new ModelQuery("Alpha",
                Optional.empty(), Optional.empty()), config, CancellationToken.none());
        assertEquals(1, discovery.candidates().size());
        assertEquals(1, discovery.failures().size());
        assertEquals(RepositoryFailure.Reason.LOAD, discovery.failures().getFirst().reason());
    }

    @Test
    void catalogRevisionIncludesLanguageDigestsPrecursorsAndFetchPolicy() {
        String uri="https://a.example/repo/ilimodels.xml";
        var source=new RepositorySource(new RepositoryBase(URI.create("https://a.example/repo/")),new SourceLocator(uri));
        var config=new RepositoryConfig(java.util.List.of(source),FetchPolicy.defaults());
        String previous=new RepositoryClient(new MapLoader(Map.of(uri,INDEX)),new RepositoryIndexReader()).catalog(config,CancellationToken.none()).revision();
        for(String changed:java.util.List.of(INDEX.replace("ili2_4","ili2_3"),INDEX.replace("</Version>","</Version><md5>abc</md5>"),
                INDEX.replace("</Version>","</Version><precursorVersion>old</precursorVersion>"))) {
            assertTrue(!previous.equals(new RepositoryClient(new MapLoader(Map.of(uri,changed)),new RepositoryIndexReader()).catalog(config,CancellationToken.none()).revision()));
        }
        var offline=new RepositoryConfig(config.sources(),new FetchPolicy(true,1000,java.time.Duration.ofSeconds(1)));
        // The injected no-IO loader supplies the same fixture; policy remains a distinct catalog input.
        assertTrue(!previous.equals(new RepositoryClient(new MapLoader(Map.of(uri,INDEX)),new RepositoryIndexReader()).catalog(offline,CancellationToken.none()).revision()));
    }

    private static final class MapLoader implements SourceLoader {
        private final Map<String, String> indexes;

        private MapLoader(Map<String, String> indexes) {
            this.indexes = indexes;
        }

        @Override
        public LoadedSource load(SourceLocator locator, FetchPolicy policy,
                                 CancellationToken token) {
            String text = indexes.get(locator.location());
            if (text == null) {
                throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,
                        "not found", null);
            }
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new LoadedSource(new SourceId(locator.location()), text,
                    guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digest(bytes)),
                    "test");
        }
    }
}

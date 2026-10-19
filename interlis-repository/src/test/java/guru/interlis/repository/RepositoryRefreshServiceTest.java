package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RepositoryRefreshServiceTest {
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
    void returnsStableRevisionAndDoesNotMutatePreviousCatalog() {
        Map<String, String> indexes = new HashMap<>();
        indexes.put("https://repo.example/ilimodels.xml", INDEX);
        RepositoryConfig config = new RepositoryConfig(List.of(
                new RepositorySource(new RepositoryBase(URI.create("https://repo.example/")),
                        new SourceLocator("https://repo.example/ilimodels.xml"))),
                FetchPolicy.defaults());
        RepositoryClient client = new RepositoryClient(new MapLoader(indexes),
                new RepositoryIndexReader());
        RepositoryRefreshService refresh = new RepositoryRefreshService(client);
        RepositoryCatalog first = refresh.refresh(config, Optional.empty(),
                CancellationToken.none());
        String firstRevision = first.revision();
        assertSame(first, refresh.refresh(config, Optional.of(first), CancellationToken.none()));

        indexes.put("https://repo.example/ilimodels.xml", INDEX.replace("<Version>1</Version>",
                "<Version>2</Version>"));
        RepositoryCatalog second = refresh.refresh(config, Optional.of(first),
                CancellationToken.none());
        assertNotEquals(firstRevision, second.revision());
        assertEquals(firstRevision, first.revision());
        assertEquals("1", first.candidates().getFirst().metadata().version());
        assertEquals("2", second.candidates().getFirst().metadata().version());
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
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            return new LoadedSource(new SourceId(locator.location()), text,
                    guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digest(bytes)),
                    "test");
        }
    }
}

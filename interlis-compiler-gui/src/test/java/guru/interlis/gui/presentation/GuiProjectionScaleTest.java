package guru.interlis.gui.presentation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.gui.application.CompileRequest;
import guru.interlis.gui.application.CompileService;
import guru.interlis.gui.application.RepositoryCatalogService;
import guru.interlis.gui.application.SessionInputs;
import guru.interlis.gui.application.SessionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuiProjectionScaleTest {
    @TempDir
    Path directory;

    @Test
    void generatedCatalogRetainsTenThousandIndependentRows() throws Exception {
        var xml = new StringBuilder("<TRANSFER>");
        for (int i = 0; i < 10_000; i++) {
            xml.append("<ModelMetadata><Name>M").append(i).append("</Name><SchemaLanguage>ili2_4</SchemaLanguage>")
                    .append("<File>source.ili</File><Version>raw</Version></ModelMetadata>");
        }
        Files.writeString(directory.resolve("ilimodels.xml"), xml.append("</TRANSFER>"));
        var browser = new RepositoryCatalogService();
        var repo = browser.repository(directory.toString(), directory, CancellationToken.none());
        var catalog = browser.load(List.of(repo), SessionOptions.defaults(), directory, 1, CancellationToken.none()).getFirst();
        assertEquals(10_000, catalog.entries().size());
        assertEquals(10_000, catalog.entries().stream().map(entry -> entry.id()).distinct().count());
    }

    @Test
    void dependencySearchRetainsAThousandModelsAndCreatesOnlyMatchingRoots() throws Exception {
        var text = new StringBuilder("INTERLIS 2.4;\n");
        for (int i = 0; i < 1000; i++) {
            text.append("MODEL M").append(i).append(" AT \"x\" VERSION \"1\" = END M").append(i).append(".\n");
        }
        Path source = Files.writeString(directory.resolve("models.ili"), text);
        var request = new CompileRequest(RunContextFactory.defaults().createRoot(), 1,
                new SessionInputs(List.of(source), List.of()), SessionOptions.defaults(), directory);
        var graph = DependencySnapshot.from(new CompileService().compile(request, CancellationToken.none()));
        assertEquals(1000, graph.models().size());
        var filtered = new DependencyTree(graph, "M999", false);
        assertEquals(List.of("M999"), filtered.roots().stream().map(DependencyTree.Occurrence::name).toList());
    }
}

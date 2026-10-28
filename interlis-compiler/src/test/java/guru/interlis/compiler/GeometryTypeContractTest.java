package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GeometryTypeContractTest {
    @Test
    void multipolylineKeepsLineFormAndVertexDomain() {
        SourceText source = new SourceText(new SourceId("memory:/geometry.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL G AT "https://example.interlis.ch" VERSION "1.0" =
                  DOMAIN Coord = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                  DOMAIN Line = MULTIPOLYLINE WITH (STRAIGHTS, ARCS) VERTEX Coord;
                END G.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("G",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        GeometryType geometry = succeeded.analysis().semanticGraph().types().stream()
                .filter(GeometryType.class::isInstance).map(GeometryType.class::cast)
                .findFirst().orElseThrow();
        assertEquals(GeometryKind.MULTIPOLYLINE, geometry.geometryKind());
        assertEquals(List.of("INTERLIS.STRAIGHTS", "INTERLIS.ARCS"), geometry.lineForms().stream()
                .map(id -> succeeded.analysis().semanticGraph().symbol(id).orElseThrow().name().value()).toList());
        assertEquals(Optional.of("Coord"), geometry.vertexDomain()
                .map(id -> succeeded.analysis().semanticGraph().symbol(id).orElseThrow().name().value()));
    }
}

package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryOwnershipTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Own AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  height : 0..999;
                END Item;
              END Data;
            END Own.
            """;

    @Test
    void oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly() {
        CompilationRequest first = request(MODEL);
        CompilationOutcome.Succeeded firstOutcome = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(first, CancellationToken.none()));
        SymbolRef oldRef = firstOutcome.schema().queries().findQualifiedName(
                QualifiedName.of("Own", "Data", "Item")).getFirst();
        assertInstanceOf(ClassSymbol.class,
                firstOutcome.schema().queries().symbol(oldRef).known().orElseThrow());
        assertTrue(firstOutcome.schema().queries().symbol(new SymbolRef(
                        firstOutcome.schema().snapshotId(),
                        new guru.interlis.model.SymbolId("missing")))
                instanceof guru.interlis.model.query.QueryResult.Unresolved<?>);
        assertThrows(UnsupportedOperationException.class,
                () -> firstOutcome.schema().semanticGraph().symbols().clear());

        String changed = MODEL.replace("height : 0..999;", "height : 0..999; !! changed");
        CompilationOutcome.Succeeded second = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request(changed), CancellationToken.none()));
        SymbolRef newRef = second.schema().queries().findQualifiedName(
                QualifiedName.of("Own", "Data", "Item")).getFirst();
        assertThrows(SnapshotMismatchException.class,
                () -> second.schema().queries().symbol(oldRef));
        assertEquals("Item", second.schema().queries().symbol(newRef).known().orElseThrow()
                .name().value());
    }

    @Test
    void sourceInsertionOrderDoesNotChangeSemanticResult() {
        SourceText a = new SourceText(new SourceId("memory:/a.ili"), new SourceRevision(1),
                MODEL.replace("MODEL Own", "MODEL Own"));
        SourceText b = new SourceText(new SourceId("memory:/b.ili"), new SourceRevision(1),
                """
                INTERLIS 2.4;
                MODEL Second AT "https://example.interlis.ch" VERSION "1.0" =
                END Second.
                """);
        Map<SourceId, SourceText> forward = new LinkedHashMap<>();
        forward.put(a.id(), a);
        forward.put(b.id(), b);
        Map<SourceId, SourceText> reverse = new LinkedHashMap<>();
        reverse.put(b.id(), b);
        reverse.put(a.id(), a);
        List<List<String>> results = List.of(forward, reverse).stream().map(sources -> {
            CompilationRequest request = new CompilationRequest(
                    List.of(new ModelSelector.ByName("Own", Optional.empty(), Optional.empty())),
                    SourceSet.of(sources), CompilerOptions.defaults());
            CompilationOutcome outcome = CompilerEngines.create().compile(request,
                    CancellationToken.none());
            return outcome instanceof CompilationOutcome.Succeeded succeeded
                    ? succeeded.schema().semanticGraph().declarationsInOrder().stream()
                    .map(s -> s.kind() + ":" + s.name().value()).toList()
                    : List.<String>of("rejected");
        }).toList();
        assertEquals(results.get(0), results.get(1));
        assertTrue(results.get(0).size() > 1);
    }

    private static CompilationRequest request(String text) {
        SourceText source = new SourceText(new SourceId("memory:/own.ili"),
                new SourceRevision(1), text);
        return new CompilationRequest(
                List.of(new ModelSelector.ByName("Own", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
    }
}

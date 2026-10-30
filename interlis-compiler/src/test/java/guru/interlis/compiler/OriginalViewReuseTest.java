package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.model.*;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.source.SourceRange;
import java.util.*;
import org.junit.jupiter.api.Test;
import static guru.interlis.compiler.OriginalViewAcceptanceSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh/reused public graph parity for all pinned originals, including failed edits. */
class OriginalViewReuseTest {
    @Test void originalGraphsAndQueriesSurviveUnchangedCommentAndInvalidEdits() throws Exception {
        var token = CancellationToken.none();
        assertEquals(19, testIds().size());
        for (var id : testIds()) {
            var original = source(id);
            var engine = CompilerEngines.create();
            AnalysisSnapshot previous = null;
            // Unchanged revision exercises reuse; comment and broken/repaired syntax exercise invalidation.
            for (int step = 0; step < 5; step++) {
                var text = switch (step) {
                    case 0, 1, 4 -> original.text();
                    case 2 -> original.text() + "\n!! acceptance comment\n";
                    case 3 -> original.text().substring(0, original.text().lastIndexOf('.'));
                    default -> throw new AssertionError();
                };
                var source = new SourceText(original.id(), new SourceRevision(Math.max(1, step)), text);
                var input = request(source);
                var reused = engine.analyze(input, previous == null ? AnalysisReuse.none()
                        : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), token);
                var fresh = CompilerEngines.create().analyze(input, token);
                if (step == 1) assertEquals(previous.semanticGraph().rootModels(), reused.semanticGraph().rootModels(), id + " unchanged model identity");
                assertEquals(fresh.diagnostics(), reused.diagnostics(), id + " step " + step);
                assertEquals(fresh.coverage(), reused.coverage(), id);
                if (step == 3) assertFalse(reused.diagnostics().isEmpty(), id);
                else assertTrue(reused.diagnostics().isEmpty(), () -> id + reused.diagnostics());
                var expected = fresh.semanticGraph(); var actual = reused.semanticGraph();
                if (step == 1 || step == 2) for (var unchanged : previous.semanticGraph().symbols()) {
                    if (!(unchanged.origin() instanceof Origin.DeclaredOrigin)) continue;
                    var unchangedKey = key(previous.semanticGraph(), unchanged.id());
                    var matching = actual.symbols().stream().filter(s -> key(actual, s.id()).equals(unchangedKey)).toList();
                    assertEquals(1, matching.size(), id);
                    assertEquals(unchanged.id(), matching.getFirst().id(), id + " unchanged " + unchanged.name());
                }
                // IDs are snapshot-local. Compare their semantic targets, not the allocator's history.
                assertEquals(semantic(expected.symbols(), expected), semantic(actual.symbols(), actual), id + " step " + step);
                assertEquals(semantic(expected.types(), expected), semantic(actual.types(), actual), id);
                assertEquals(semantic(expected.expressions(), expected), semantic(actual.expressions(), actual), id);
                assertEquals(semantic(expected.references(), expected), semantic(actual.references(), actual), id);
                for (var symbol : actual.symbols()) {
                    var corresponding = expected.symbols().stream().filter(s -> key(expected, s.id()).equals(key(actual, symbol.id()))).findFirst().orElseThrow();
                    var expectedRef = new SymbolRef(fresh.snapshotId(), corresponding.id());
                    var actualRef = new SymbolRef(reused.snapshotId(), symbol.id());
                    assertEquals(semantic(fresh.queries().symbol(expectedRef), expected), semantic(reused.queries().symbol(actualRef), actual), id);
                    assertEquals(semantic(fresh.queries().declaredType(expectedRef), expected), semantic(reused.queries().declaredType(actualRef), actual), id);
                    assertEquals(semantic(fresh.queries().effectiveMembers(expectedRef), expected), semantic(reused.queries().effectiveMembers(actualRef), actual), id);
                    symbol.nameRange().ifPresent(range -> assertEquals(source.revision(), range.revision(), id));
                }
                if (step != 3) for (var reference : actual.references()) for (var segment : reference.segments()) {
                    assertEquals(source.revision(), segment.range().revision(), id);
                    if (segment.target().isEmpty()) continue;
                    var range = segment.range();
                    var cursor = new SourceRange(source.id(), source.revision(), range.startOffset(), range.startOffset());
                    assertEquals(segment.target().orElseThrow(), LanguageQueries.create().symbolAt(reused, cursor, token).known().orElseThrow().symbol(), id);
                    if (source.revision().value() > 1) {
                        var stale = new SourceRange(source.id(), new SourceRevision(source.revision().value() - 1), range.startOffset(), range.startOffset());
                        assertThrows(IllegalArgumentException.class, () -> LanguageQueries.create().symbolAt(reused, stale, token), id);
                    }
                }
                previous = reused;
            }
        }
    }

    private static String key(SemanticGraph graph, SymbolId id) {
        var symbol = graph.symbol(id).orElseThrow();
        return symbol.owner().map(owner -> key(graph, owner) + "/").orElse("") + symbol.kind() + ":" + symbol.name().value();
    }

    /** Preserve every record component and list order; normalize only snapshot-local identities. */
    static Object semantic(Object value, SemanticGraph graph) {
        if (value instanceof SymbolId id) return key(graph, id);
        if (value instanceof ExpressionId id) {
            String normalized=id.value();
            // Derived expression ids contain bound symbol identities. Preserve every
            // suffix and resolve each actual symbol rather than ignoring the id.
            for(var symbol:graph.symbols().stream().sorted(java.util.Comparator.comparingInt((Symbol symbol)->symbol.id().value().length()).reversed()).toList())
                normalized=normalized.replaceAll(java.util.regex.Pattern.quote(symbol.id().value())+"(?=[:$]|$)",java.util.regex.Matcher.quoteReplacement(key(graph,symbol.id())));
            return List.of("expression-identity",normalized);
        }
        if (value instanceof ScopeId scope && graph.symbol(new SymbolId(scope.value())).isPresent()) return key(graph, new SymbolId(scope.value()));
        if (value instanceof Optional<?> optional) return optional.map(v -> semantic(v, graph));
        if (value instanceof Set<?> set) return set.stream().map(v -> semantic(v, graph)).collect(java.util.stream.Collectors.toSet());
        if (value instanceof Collection<?> list) return list.stream().map(v -> semantic(v, graph)).toList();
        if (value instanceof Map<?, ?> map) {
            var result = new HashMap<Object, Object>(); map.forEach((k, v) -> result.put(semantic(k, graph), semantic(v, graph))); return result;
        }
        if (!value.getClass().isRecord()) return value;
        var fields = new ArrayList<Object>(); fields.add(value.getClass().getName());
        for (var field : value.getClass().getRecordComponents()) try { fields.add(semantic(field.getAccessor().invoke(value), graph)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        return fields;
    }
}

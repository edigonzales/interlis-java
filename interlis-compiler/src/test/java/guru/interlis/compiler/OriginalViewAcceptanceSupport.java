package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact upstream inputs plus shared public-query checks; case expectations stay in their tests. */
final class OriginalViewAcceptanceSupport {
    private static final Path DIRECTORY = Path.of("../testdata/conformance/b1-b3-acceptance");
    private static final CancellationToken TOKEN = CancellationToken.none();
    private OriginalViewAcceptanceSupport() { }

    static List<String> testIds() throws Exception {
        var pins = new Properties();
        try (var reader = Files.newBufferedReader(DIRECTORY.resolve("sources.properties"), StandardCharsets.UTF_8)) { pins.load(reader); }
        return pins.stringPropertyNames().stream().sorted().toList();
    }

    static SourceText source(String testId) throws Exception {
        var pins = new Properties();
        try (var reader = Files.newBufferedReader(DIRECTORY.resolve("sources.properties"), StandardCharsets.UTF_8)) { pins.load(reader); }
        var entry = Objects.requireNonNull(pins.getProperty(testId), testId).split("\\|", 3);
        var bytes = Files.readAllBytes(DIRECTORY.resolve(entry[0]));
        assertEquals(entry[1], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "original source pin: " + testId);
        return new SourceText(new SourceId("memory:/b1-b3/" + entry[0]), new SourceRevision(1), new String(bytes, StandardCharsets.UTF_8));
    }

    static CompilationRequest request(SourceText source) {
        var syntax = CompilerTestSupport.parse(source);
        return CompilerTestSupport.request(syntax.root().models().stream().map(m -> m.name()).toList(),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
    }

    static CompilationOutcome.Succeeded original(String testId) throws Exception {
        return compile(source(testId));
    }

    static CompilationOutcome.Succeeded compile(SourceText source) {
        var result = CompilerEngines.create().compile(request(source), TOKEN);
        var success = assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
        assertTrue(success.analysis().diagnostics().isEmpty(), success.analysis().diagnostics()::toString);
        assertTrue(success.analysis().coverage().incompleteEntries().isEmpty());
        var graph = success.schema().semanticGraph(); var queries = success.schema().queries();
        for (var expression : graph.expressions()) {
            assertEquals(ExpressionTypeState.RESOLVED, expression.typeState(), expression::toString);
            assertEquals(expression, queries.expression(new ExpressionRef(graph.snapshotId(), expression.id())).known().orElseThrow());
        }
        for (var symbol : graph.symbols()) if (symbol instanceof ViewSymbol view) {
            var effective = queries.effectiveMembers(new SymbolRef(graph.snapshotId(), view.id())).known().orElseThrow();
            assertEquals(view.members(), effective.stream().map(EffectiveMember::declaredSymbol).toList(), view::toString);
            for (int i = 0; i < effective.size(); i++) assertEquals(i, effective.get(i).position());
        }
        for (var reference : graph.references()) {
            var target = assertInstanceOf(BindingResolution.BoundTarget.class, reference.resolution());
            assertEquals(target.target(), queries.resolveReference(reference.id()).known().orElseThrow().symbol());
        }
        return success;
    }

    static Symbol named(CompilationOutcome.Succeeded result, String qualified) {
        var refs = result.schema().queries().findQualifiedName(QualifiedName.of(qualified.split("\\.")));
        assertEquals(1, refs.size(), qualified);
        return result.schema().queries().symbol(refs.getFirst()).known().orElseThrow();
    }
    static ViewSymbol view(CompilationOutcome.Succeeded result, String qualified) { return assertInstanceOf(ViewSymbol.class, named(result, qualified)); }
    static AttributeSymbol attribute(CompilationOutcome.Succeeded result, String qualified) { return assertInstanceOf(AttributeSymbol.class, named(result, qualified)); }
    static TypeDescriptor effective(CompilationOutcome.Succeeded result, TypeId type) {
        var schema = result.schema();
        var resolved = new TypeRelations(schema.semanticGraph()).resolveAliases(new TypeRef(schema.snapshotId(), type)).known().orElseThrow();
        return schema.queries().type(new TypeRef(schema.snapshotId(), resolved.effectiveType())).known().orElseThrow();
    }
    static PathExpression path(CompilationOutcome.Succeeded result, ExpressionId id) {
        return assertInstanceOf(PathExpression.class, result.schema().queries().expression(new ExpressionRef(result.schema().snapshotId(), id)).known().orElseThrow());
    }
    static List<String> names(CompilationOutcome.Succeeded result, List<SymbolId> ids) {
        return ids.stream().map(id -> result.schema().queries().symbol(new SymbolRef(result.schema().snapshotId(), id)).known().orElseThrow().name().value()).toList();
    }
    static void navigation(CompilationOutcome.Succeeded result, SourceRange range, SymbolId target) {
        var at = new SourceRange(range.source(), range.revision(), range.startOffset(), range.startOffset());
        assertEquals(target, LanguageQueries.create().symbolAt(result.analysis(), at, TOKEN).known().orElseThrow().symbol());
    }
}

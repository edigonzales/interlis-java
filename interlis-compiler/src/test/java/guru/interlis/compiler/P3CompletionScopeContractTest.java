package guru.interlis.compiler;

import guru.interlis.compiler.query.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** LSP-007: shared completion respects lexical scope, syntax range and language. */
class P3CompletionScopeContractTest {
    private static final String TEXT = """
            INTERLIS 2.4;
            MODEL F AT "x" VERSION "1" =
              DOMAIN OuterName = TEXT;
              TOPIC T =
                CLASS A =
                  localName: TEXT;
                  target: OuterName;
                END A;
                CLASS B = foreignName: TEXT; END B;
              END T;
            END F.
            """;

    @Test void ordinaryCompletionOffersLocalAndOuterNamesButNoForeignMembers() {
        var source = source(TEXT); var analysis = analyze(source);
        var result = LanguageQueries.create().complete(analysis,
                new CompletionQuery(source.range(TEXT.indexOf("target"), TEXT.indexOf("target")), NamePrefix.empty(), 1000),
                CancellationToken.none());
        var names = result.candidates().stream().filter(c -> c.kind()!=CompletionKind.KEYWORD).map(CompletionCandidate::label).toList();
        assertTrue(names.contains("localName"), names.toString());
        assertTrue(names.contains("OuterName"), names.toString());
        assertFalse(names.contains("foreignName"), names.toString());
        assertTrue(names.indexOf("localName") < names.indexOf("OuterName"));
    }

    @Test void completionReplacesTheWholeCurrentNameAndReportsOnlyRealTruncation() {
        var source = source(TEXT); var analysis = analyze(source);
        int start = TEXT.lastIndexOf("OuterName"), cursor = start + 6;
        var result = LanguageQueries.create().complete(analysis,
                new CompletionQuery(source.range(cursor, cursor), new NamePrefix("OuterN"), 1), CancellationToken.none());
        assertEquals(List.of("OuterName"), result.candidates().stream().map(CompletionCandidate::insertText).toList());
        assertEquals(source.range(start, start + "OuterName".length()), result.replacementRange());
        assertFalse(result.incomplete(), "exactly one available candidate is not a truncation");
    }

    @Test void syntaxCandidatesUseTheRequestedLanguageAndRemainDistinctFromSemanticCandidates() {
        for (String version : List.of("2.3", "2.4")) {
            var source = source(TEXT.replace("2.4", version)); var analysis = analyze(source);
            int offset = source.text().indexOf("target");
            var result = LanguageQueries.create().complete(analysis,
                    new CompletionQuery(source.range(offset, offset), new NamePrefix("MULTICO"), 100), CancellationToken.none());
            assertEquals(version.equals("2.4"), result.candidates().stream().anyMatch(candidate -> candidate.label().equals("MULTICOORD")));
            assertTrue(result.candidates().stream().allMatch(candidate -> candidate.kind()==CompletionKind.KEYWORD));
        }
    }

    @Test void qualifiedCompletionUsesOnlyTheActualQualifierMembers() {
        String text = TEXT.replace("TOPIC T =", "TOPIC T = DOMAIN ScopedType = TEXT;")
                .replace("target: OuterName", "target: F.T.ScopedType");
        var source = source(text); var analysis = analyze(source);
        int cursor = text.lastIndexOf("ScopedType");
        var result = LanguageQueries.create().complete(analysis,
                new CompletionQuery(source.range(cursor,cursor),NamePrefix.empty(),1000),CancellationToken.none());
        var names = result.candidates().stream().map(CompletionCandidate::label).toList();
        assertTrue(names.containsAll(List.of("ScopedType","A","B")), names.toString());
        assertFalse(names.contains("OuterName"),names.toString());
        assertFalse(names.contains("localName"),names.toString());
        assertTrue(result.candidates().stream().noneMatch(candidate -> candidate.kind()==CompletionKind.KEYWORD));
        assertThrows(UnsupportedOperationException.class, () -> analysis.queries().membersOf(
                analysis.queries().findQualifiedName(QualifiedName.of("F","T")).getFirst()).known().orElseThrow().clear());
    }

    @Test void renameRejectsNewOuterAndInheritedTypeCollisionsInEitherDirection() {
        for(String text:List.of(TEXT,TEXT.replace("CLASS B =", "CLASS B EXTENDS A ="))) {
            var source=source(text); var snapshot=analyze(source); var queries=LanguageQueries.create();
            for(var pair:List.of(List.of("F.T.A","OuterName"),List.of("F.OuterName","A"))) {
                var target=snapshot.queries().findQualifiedName(QualifiedName.of(pair.getFirst().split("\\."))).getFirst();
                var plan=queries.planRename(snapshot,new RenameRequest(target,pair.get(1),snapshot.sourceRevisions()),CancellationToken.none());
                assertFalse(plan.complete(),pair.toString()); assertTrue(plan.edits().isEmpty());
            }
        }
    }

    @Test void incompleteReferenceSearchAndMissingRevisionNeverClaimACompleteRename() {
        var source=source(TEXT); var snapshot=analyze(source); var queries=LanguageQueries.create();
        var target=snapshot.queries().findQualifiedName(QualifiedName.of("F","OuterName")).getFirst();
        assertFalse(queries.planRename(snapshot,new RenameRequest(target,"NewType",Map.of()),CancellationToken.none()).complete());
        var broken=analyze(source(TEXT.replace("target: OuterName","target: MissingType")));
        var selected=broken.queries().findQualifiedName(QualifiedName.of("F","OuterName")).getFirst();
        assertFalse(queries.references(broken,selected,new ReferenceOptions(true),CancellationToken.none()).complete());
        assertFalse(queries.planRename(broken,new RenameRequest(selected,"NewType",broken.sourceRevisions()),CancellationToken.none()).complete());
    }

    @Test void scopeAndMemberQueriesRejectForeignSnapshotsAndKeepUnknownScopesTyped() {
        var snapshot=analyze(source(TEXT));
        var target=snapshot.queries().findQualifiedName(QualifiedName.of("F","T")).getFirst();
        assertThrows(SnapshotMismatchException.class,()->snapshot.queries().membersOf(new SymbolRef(new SnapshotId("foreign"),target.symbol())));
        assertThrows(SnapshotMismatchException.class,()->snapshot.queries().visibleSymbols(new ScopeRef(new SnapshotId("foreign"),ScopeId.forOwner(target.symbol())),NamePrefix.empty()));
        var missing=snapshot.queries().visibleSymbols(new ScopeRef(snapshot.snapshotId(),new ScopeId("missing-owner")),NamePrefix.empty());
        assertInstanceOf(guru.interlis.model.query.QueryResult.Unresolved.class,missing);
    }

    private static SourceText source(String text) { return new SourceText(new SourceId("memory:/completion.ili"), new SourceRevision(1), text); }
    private static AnalysisSnapshot analyze(SourceText source) {
        return CompilerEngines.create().analyze(new CompilationRequest(List.of(new ModelSelector.BySource(source.id())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults()), CancellationToken.none());
    }
}

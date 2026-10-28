package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.compiler.query.RenameRequest;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenameSafetyTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final LanguageQueries QUERIES = LanguageQueries.create();

    private static AnalysisSnapshot analyze(SourceText... sources) {
        var map = new java.util.LinkedHashMap<guru.interlis.source.SourceId, SourceText>();
        for (var source : sources) map.put(source.id(), source);
        var request = CompilerTestSupport.request(map.values().stream().flatMap(s -> CompilerTestSupport.parse(s).root().models().stream()).map(m -> m.name()).toList(), map, List.of(), CompilerOptions.defaults());
        return CompilerEngines.create().analyze(request, AnalysisReuse.none(), TOKEN);
    }

    private static SourceRange cursor(SourceText source, int offset) {
        return new SourceRange(source.id(), source.revision(), offset, offset);
    }

    @Test
    void sameOffsetInAnotherSourceDoesNotSelectItsDeclaration() {
        var a = CompilerTestSupport.source("memory:/a.ili",
                "INTERLIS 2.4; MODEL A AT \"x\" VERSION \"1\" = TOPIC T = CLASS C = END C; END T; END A.");
        var b = CompilerTestSupport.source("memory:/b.ili", a.text().replace("MODEL A", "MODEL B").replace("END A", "END B").replace("C =", "D =")
                .replace("END C", "END D"));
        var analysis = analyze(a, b);
        var selected = QUERIES.symbolAt(analysis, cursor(b, b.text().indexOf("D =")), TOKEN)
                .known().orElseThrow();
        assertEquals("D", analysis.queries().symbol(selected).known().orElseThrow().name().value());
    }

    @Test
    void renameDoesNotTouchSameNameInAnotherTopicOrComments() {
        var source = CompilerTestSupport.source("memory:/rename.ili", """
                INTERLIS 2.4;
                MODEL M AT "x" VERSION "1" =
                  TOPIC T1 = CLASS C = END C; END T1;
                  TOPIC T2 = CLASS C = END C; END T2;
                  !! END C;
                END M.
                """);
        var analysis = analyze(source);
        var selected = QUERIES.symbolAt(analysis, cursor(source, source.text().indexOf("C =")), TOKEN)
                .known().orElseThrow();
        var plan = QUERIES.planRename(analysis,
                new RenameRequest(selected, "D", analysis.sourceRevisions()), TOKEN);
        assertTrue(plan.complete(), plan.toString());
        assertEquals(2, plan.edits().getFirst().edits().size());
        for (var edit : plan.edits().getFirst().edits()) {
            assertTrue(edit.range().endOffset() < source.text().indexOf("TOPIC T2"));
        }
        for (String invalid : List.of("MODEL", "D".repeat(256))) {
            assertFalse(QUERIES.planRename(analysis,
                    new RenameRequest(selected, invalid, analysis.sourceRevisions()), TOKEN).complete(), invalid);
        }
        for (String invalid : List.of("D ", " D", "D$", "_D")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new RenameRequest(selected, invalid, analysis.sourceRevisions()));
        }
    }

    @Test
    void prepareRenameAndQualifiedReferencesUseTheSelectedSegment() {
        var base = CompilerTestSupport.source("memory:/base.ili",
                "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = DOMAIN Count = 0..100; END Base.");
        var use = CompilerTestSupport.source("memory:/use.ili",
                "INTERLIS 2.4; MODEL Use AT \"x\" VERSION \"1\" = IMPORTS Base;"
                + " TOPIC T = CLASS C = value : Base.Count; END C; END T; END Use.");
        var request = CompilerTestSupport.request(List.of("Use"),
                Map.of(base.id(), base, use.id(), use),
                List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(use), "Base",
                        base, CompilerTestSupport.parse(base))), CompilerOptions.defaults());
        var analysis = CompilerEngines.create().analyze(request, AnalysisReuse.none(), TOKEN);
        int prefix = use.text().indexOf("Base.Count");
        var prepared = QUERIES.prepareRename(analysis, cursor(use, prefix), TOKEN);
        assertTrue(prepared.allowed(), prepared.toString());
        assertEquals(use.range(prefix, prefix + 4), prepared.range().orElseThrow());
        var selected = QUERIES.symbolAt(analysis, cursor(use, prefix), TOKEN).known().orElseThrow();
        assertEquals("Base", analysis.queries().symbol(selected).known().orElseThrow().name().value());
        var plan = QUERIES.planRename(analysis,
                new RenameRequest(selected, "RenamedBase", analysis.sourceRevisions()), TOKEN);
        assertTrue(plan.complete(), plan.toString());
        assertEquals(4, plan.edits().stream().mapToInt(e -> e.edits().size()).sum());
        var count = QUERIES.symbolAt(analysis, cursor(use, prefix + 5), TOKEN).known().orElseThrow();
        assertEquals("Count", analysis.queries().symbol(count).known().orElseThrow().name().value());
    }
}

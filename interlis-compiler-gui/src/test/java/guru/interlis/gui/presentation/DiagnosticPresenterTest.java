package guru.interlis.gui.presentation;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.RelatedLocation;
import guru.interlis.diagnostics.Severity;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticPresenterTest {
    @Test
    void filteringAndCauseGroupingRetainCanonicalCountsAndLinks() {
        var first = new SourceText(new SourceId("memory:/first.ili"), new SourceRevision(2), "x\r\näbc");
        var second = new SourceText(new SourceId("memory:/second.ili"), new SourceRevision(5), "z");
        var cause = diagnostic("root", Severity.ERROR, Optional.of(first.range(3, 4)), Optional.empty(), List.of());
        var child = diagnostic("child", Severity.WARNING, Optional.of(second.range(0, 1)), Optional.of(cause.id()), List.of(cause.id()));
        var global = diagnostic("global", Severity.ERROR, Optional.empty(), Optional.empty(), List.of());
        var originals = List.of(cause, child, global);
        var presenter = new DiagnosticPresenter(originals, Map.of(first.id(), snapshot(first), second.id(), snapshot(second)),
                DiagnosticLanguage.EN);

        var grouped = presenter.present(EnumSet.allOf(Severity.class), "", true, Optional.empty());
        assertEquals(3, grouped.total());
        assertEquals(1, grouped.grouped());
        assertEquals(List.of(cause.id(), global.id()), grouped.visible().stream().map(DiagnosticPresenter.Row::id).toList());
        assertEquals(List.of(child.id()), presenter.dependents(cause.id()));
        assertEquals(child, presenter.details(child.id()).orElseThrow().diagnostic());

        var filtered = presenter.present(EnumSet.of(Severity.WARNING), "second", true, Optional.empty());
        assertEquals(3, filtered.total());
        assertEquals(List.of(child.id()), filtered.visible().stream().map(DiagnosticPresenter.Row::id).toList());
        assertTrue(grouped.visible().getLast().position().isEmpty());
        assertEquals("No source location", presenter.details(global.id()).orElseThrow().location());
    }

    @Test
    void usesSharedLocalizedMessageHintAndRelatedRangeWithoutInventingMissingCoordinates() {
        var source = new SourceText(new SourceId("memory:/büro.ili"), new SourceRevision(5), "Office\r\nOffice");
        var related = new RelatedLocation(source.range(0, 6), "diagnostic.related.conflictingDeclaration",
                List.of(new DiagnosticArgument.IdentifierArgument("name", "Office")));
        var diagnostic = new Diagnostic(new DiagnosticId("conflict"), new DiagnosticCode("ILI-SEM-0010"), Severity.ERROR,
                Phase.SEMANTIC_CHECKING, "diagnostic.semantics.inheritance",
                List.of(new DiagnosticArgument.TextArgument("rule", "scope/SCOPE_OUTER_TYPE_NAME"),
                        new DiagnosticArgument.IdentifierArgument("element", "Office")),
                Optional.of(source.range(8, 14)), List.of(related), Optional.empty(), List.of(), Optional.empty(),
                Optional.empty(), List.of());
        var exact = new DiagnosticPresenter(List.of(diagnostic), Map.of(source.id(), snapshot(source)), DiagnosticLanguage.DE);
        assertTrue(exact.details(diagnostic.id()).orElseThrow().message().hint().orElseThrow().contains("unterschiedliche"));
        assertTrue(exact.details(diagnostic.id()).orElseThrow().related().getFirst().message().contains("Kollidierende"));
        assertEquals(related.range(), exact.details(diagnostic.id()).orElseThrow().related().getFirst().range());
        assertSame(source, exact.source(related.range()).orElseThrow());
        var stale = new SourceText(source.id(), new SourceRevision(6), source.text());
        var unavailable = new DiagnosticPresenter(List.of(diagnostic), Map.of(source.id(), snapshot(stale)), DiagnosticLanguage.EN);
        assertTrue(unavailable.source(related.range()).isEmpty());
        assertTrue(unavailable.present(EnumSet.allOf(Severity.class), "", false, Optional.empty())
                .visible().getFirst().position().isEmpty());
    }

    private static Diagnostic diagnostic(String id, Severity severity, Optional<SourceRange> range,
                                         Optional<DiagnosticId> cause, List<DiagnosticId> depends) {
        return new Diagnostic(new DiagnosticId(id), new DiagnosticCode("ILI-SYN-0002"), severity,
                Phase.PARSING, "diagnostic.syntax.unexpected", List.of(), range, List.of(), cause, depends,
                Optional.empty(), Optional.empty(), List.of());
    }

    private static CompileResult.SourceSnapshot snapshot(SourceText source) {
        return new CompileResult.SourceSnapshot(source, Optional.empty(), CompileResult.Origin.LOCAL_INPUT);
    }
}

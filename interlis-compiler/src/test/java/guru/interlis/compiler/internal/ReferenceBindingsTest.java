package guru.interlis.compiler.internal;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.BindingResolution;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.NameReference;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.ReferenceSegment;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.syntax.NameSyntax;
import guru.interlis.syntax.SourceMetadata;
import guru.interlis.syntax.SyntaxNodeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceBindingsTest {
    @Test
    void deduplicatesOnlyTheSameOwnerRangeUsageAndTargetBeforeAllocating() {
        var source = new SourceId("memory:/references.ili");
        var range = new SourceRange(source, SourceRevision.initial(), 2, 3);
        var name = new NameSyntax(new SyntaxNodeId("name"), range,
                List.of(new NameSyntax.NameSegmentSyntax("X", range)));
        var owner = owner("owner", name);
        var target = new SymbolId("target");
        var other = new SymbolId("other-target");
        var declarations = new IndexedDeclarations(List.of(), Map.of(), Map.of(), Map.of(), Map.of());
        var recorded = new ArrayList<NameReference>();
        var bindings = new ReferenceBindings(declarations, new SemanticBuildState(),
                new SemanticWork(CancellationToken.none(), 10, 0), recorded::add, PredefinedModels::id);

        bindings.recordReference(owner, name, target, ReferenceUsage.EXPRESSION);
        bindings.recordEnumReference(owner.id(), range, List.of(new ReferenceSegment("X", range, Optional.of(target))), target);
        bindings.recordReference(owner, name, target, ReferenceUsage.TYPE_USE);
        bindings.recordReference(owner, name, other, ReferenceUsage.EXPRESSION);
        bindings.recordReference(owner("other-owner", name), name, target, ReferenceUsage.EXPRESSION);

        var shifted = new SourceRange(source, SourceRevision.initial(), 4, 5);
        var secondName = new NameSyntax(new SyntaxNodeId("other-name"), shifted,
                List.of(new NameSyntax.NameSegmentSyntax("X", shifted)));
        bindings.recordReference(owner, secondName, target, ReferenceUsage.EXPRESSION);
        bindings.recordReference(owner, secondName, null, ReferenceUsage.EXPRESSION);
        assertEquals(List.of("ref-1", "ref-2", "ref-3", "ref-4", "ref-5", "ref-6"),
                recorded.stream().map(NameReference::id).map(ReferenceId::value).toList());
        assertEquals(List.of(new ReferenceSegment("X", range, Optional.of(target))), recorded.getFirst().segments());
        assertEquals(new BindingResolution.BoundTarget(other), recorded.get(2).resolution());
        assertEquals(shifted, recorded.get(4).range());
        assertInstanceOf(BindingResolution.UnresolvedTarget.class, recorded.getLast().resolution());
    }

    private static DeclInfo owner(String id, NameSyntax name) {
        return new DeclInfo(new SymbolId(id), SymbolKind.CLASS, id, Optional.empty(), 0,
                new Origin.DeclaredOrigin(name.range()), Optional.of(name.range()), LanguageVersion.ILI24,
                Set.of(), name, name.range().source(), id, Optional.empty(), SourceMetadata.empty());
    }
}

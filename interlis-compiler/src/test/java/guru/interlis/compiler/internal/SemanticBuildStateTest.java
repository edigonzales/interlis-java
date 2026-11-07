package guru.interlis.compiler.internal;

import guru.interlis.model.BooleanType;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.ConstantExpression;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveMemberOrigin;
import guru.interlis.model.ExpressionTypeState;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.Origin;
import guru.interlis.model.PendingReason;
import guru.interlis.model.PendingType;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeId;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SemanticBuildStateTest {
    @Test
    void definitionAndReplacementPreserveOrderAndRejectUnexpectedWrites() {
        var state = new SemanticBuildState();
        var first = declaration("first", "First");
        var second = declaration("second", "Second");
        var replacement = new ClassSymbol(first.header(), Optional.empty(), List.of(), List.of(new SymbolId("constraint")));
        state.defineSymbol(first);
        var live = state.symbolsInBuildOrder();
        var earlier = List.copyOf(live);
        state.defineSymbol(second);
        assertSame(first, state.symbol(first.id()));
        assertThrows(IllegalStateException.class, () -> state.defineSymbol(replacement));
        assertThrows(IllegalStateException.class, () -> state.replaceSymbol(declaration("missing", "Missing")));
        assertThrows(IllegalStateException.class, () -> state.replaceSymbol(declaration("first", "Different")));
        state.replaceSymbol(replacement);
        assertEquals(List.of(replacement, second), List.copyOf(live));
        assertEquals(List.of(first), earlier);
        assertThrows(UnsupportedOperationException.class, live::clear);
    }

    @Test
    void effectiveMembersAndDeclaredTypeEntriesExposeNoMutableAliases() {
        var state = new SemanticBuildState();
        var owner = new SymbolId("owner");
        var member = new EffectiveMember(new SymbolId("member"), owner, List.of(owner), 0,
                EffectiveMemberOrigin.LOCAL, false);
        var input = new ArrayList<>(List.of(member));
        state.setEffectiveMembers(owner, input);
        input.clear();
        assertEquals(List.of(member), state.effectiveMembers(owner));
        assertThrows(UnsupportedOperationException.class, state.effectiveMembers(owner)::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> state.effectiveMembersInBuildOrder().iterator().next().setValue(List.of()));

        var reserved = state.nextTypeId();
        var other = state.nextTypeId();
        state.bindDeclaredType(owner, reserved);
        assertThrows(IllegalStateException.class, () -> state.bindDeclaredType(owner, other));
        assertEquals(reserved, state.declaredType(owner));
        assertThrows(UnsupportedOperationException.class,
                () -> state.declaredTypesInBuildOrder().iterator().next().setValue(other));
    }

    @Test
    void typeCompletionAndExpressionRetypingKeepTheirAllocatedIdentities() {
        var state = new SemanticBuildState();
        var source = new SourceText(new SourceId("memory:/state.ili"), new SourceRevision(1), "PI");
        var range = source.range(0, 2);
        var origin = new Origin.DeclaredOrigin(range);
        var reserved = state.nextTypeId();
        var other = state.nextTypeId();
        var pending = new PendingType(reserved, origin, PendingReason.NOT_ANALYZED);
        var completed = new BooleanType(reserved, origin);
        var otherType = new BooleanType(other, origin);
        assertThrows(IllegalStateException.class, () -> state.replaceType(completed));
        state.defineType(pending);
        assertThrows(IllegalStateException.class, () -> state.defineType(completed));
        state.defineType(otherType);
        assertThrows(IllegalStateException.class,
                () -> state.replaceType(new BooleanType(reserved, new Origin.DeclaredOrigin(source.range(1, 2)))));
        state.replaceType(completed);

        var id = state.nextExpressionId();
        var original = new ConstantExpression(id, other, ExpressionTypeState.RESOLVED, range, List.of(), "PI");
        var retyped = new ConstantExpression(id, reserved, ExpressionTypeState.RESOLVED, range, List.of(), "PI");
        assertThrows(IllegalStateException.class, () -> state.replaceExpression(retyped));
        state.defineExpression(original);
        assertThrows(IllegalStateException.class, () -> state.defineExpression(retyped));
        assertThrows(IllegalStateException.class, () -> state.replaceExpression(
                new ConstantExpression(id, reserved, ExpressionTypeState.RESOLVED, source.range(1, 2), List.of(), "PI")));
        state.replaceExpression(retyped);

        var graph = state.freeze(new SnapshotId("graph"), List.of(), List.of(), Set.of(),
                Map.of(source.id(), source.revision()));
        assertEquals(List.of(completed, otherType), List.copyOf(graph.types()));
        assertEquals(List.of(retyped), List.copyOf(graph.expressions()));
        assertThrows(IllegalStateException.class, () -> state.defineType(pending));
        assertThrows(IllegalStateException.class, () -> state.replaceType(completed));
        assertThrows(IllegalStateException.class, () -> state.defineExpression(original));
        assertThrows(IllegalStateException.class, () -> state.replaceExpression(retyped));
        assertThrows(IllegalStateException.class, state::nextTypeId);
        assertThrows(IllegalStateException.class, state::nextTypeSequence);
    }

    @Test
    void publicationClosesTheStoreAndLeavesOtherRunsIndependent() {
        var state = new SemanticBuildState();
        var first = declaration("a", "First");
        state.defineSymbol(first);
        var nextRun = new SemanticBuildState();
        assertEquals(state.nextTypeId(), nextRun.nextTypeId());
        assertEquals(state.nextExpressionId(), nextRun.nextExpressionId());

        var graph = state.freeze(new SnapshotId("first"), List.of(), List.of(), Set.of(), Map.of());
        nextRun.defineSymbol(declaration("a", "Other"));
        assertEquals(List.of(first), List.copyOf(graph.symbols()));
        assertThrows(UnsupportedOperationException.class, graph.symbols()::clear);
        assertThrows(IllegalStateException.class, () -> state.replaceSymbol(declaration("a", "Replaced")));
        assertThrows(IllegalStateException.class, () -> state.bindBase(first.id(), first.id()));
        assertThrows(IllegalStateException.class, () -> state.bindDeclaredType(first.id(), new TypeId("late")));
        assertThrows(IllegalStateException.class, state::nextExpressionId);
        assertThrows(IllegalStateException.class,
                () -> state.freeze(new SnapshotId("second"), List.of(), List.of(), Set.of(), Map.of()));
    }

    private static Symbol declaration(String id, String name) {
        var header = new SymbolHeader(new SymbolId(id), SymbolKind.CLASS, DeclaredName.declared(name),
                Optional.empty(), 0, new Origin.PredefinedOrigin(LanguageVersion.ILI24, name),
                Optional.empty(), LanguageVersion.ILI24, Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of());
        return new ClassSymbol(header, Optional.empty(), List.of(), List.of());
    }
}

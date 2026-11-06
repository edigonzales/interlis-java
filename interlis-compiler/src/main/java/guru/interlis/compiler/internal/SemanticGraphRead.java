package guru.interlis.compiler.internal;

import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read operations over the current graph as it is assembled in one run.
 * Missing individual values return null. Collection views are read-only and preserve build order.
 */
interface SemanticGraphRead {
    Symbol symbol(SymbolId id);
    boolean hasSymbol(SymbolId id);
    TypeDescriptor type(TypeId id);
    TypedExpression expression(ExpressionId id);
    TypeId declaredType(SymbolId id);
    boolean hasDeclaredType(SymbolId id);
    SymbolId baseOf(SymbolId id);
    boolean hasBase(SymbolId id);
    Collection<Symbol> symbolsInBuildOrder();
    Set<Map.Entry<SymbolId, TypeId>> declaredTypesInBuildOrder();
    List<EffectiveMember> effectiveMembers(SymbolId owner);
    Set<Map.Entry<SymbolId, List<EffectiveMember>>> effectiveMembersInBuildOrder();

    /** The portion of build state responsible for defining and completing symbols. */
    interface Symbols extends SemanticGraphRead {
        void defineSymbol(Symbol symbol);
        void replaceSymbol(Symbol symbol);
    }

    /** Type descriptors, their allocation sequence, and declaration assignments. */
    interface Types extends SemanticGraphRead {
        void defineType(TypeDescriptor type);
        void replaceType(TypeDescriptor type);
        TypeId nextTypeId();
        int nextTypeSequence();
        void bindDeclaredType(SymbolId symbol, TypeId type);
        void setEffectiveType(SymbolId symbol, EffectiveType type);
    }

    /** Bound expressions and their allocation sequence. */
    interface Expressions extends SemanticGraphRead {
        void defineExpression(TypedExpression expression);
        void replaceExpression(TypedExpression expression);
        ExpressionId nextExpressionId();
    }

    /** Canonical base edges and effective member lists. */
    interface Inheritance extends SemanticGraphRead {
        void bindBase(SymbolId symbol, SymbolId base);
        void setEffectiveMembers(SymbolId owner, List<EffectiveMember> members);
    }
}

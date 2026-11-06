package guru.interlis.compiler.internal;

import guru.interlis.model.ExpressionId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Read operations over the current graph as it is assembled in one run. */
interface SemanticGraphRead {
    Symbol symbol(SymbolId id);
    TypeDescriptor type(TypeId id);
    TypedExpression expression(ExpressionId id);
    TypeId declaredType(SymbolId id);
    SymbolId baseOf(SymbolId id);
    Collection<Symbol> symbolsInBuildOrder();
    Set<Map.Entry<SymbolId, TypeId>> declaredTypesInBuildOrder();
}

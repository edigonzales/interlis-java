package guru.interlis.model.query;

import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.ExpressionRef;
import guru.interlis.model.LookupKind;
import guru.interlis.model.NamePrefix;
import guru.interlis.model.NameReference;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.ScopeRef;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.TypedExpression;

import java.util.List;
import java.util.Optional;

/** Read-only semantic view bound to exactly one snapshot. */
public interface SemanticQueries {

    /**
     * Returns the snapshot to which every reference in this view must belong.
     *
     * @return bound snapshot id
     */
    SnapshotId snapshotId();

    /**
     * Returns the immutable semantic graph of this snapshot.
     *
     * @return graph bound to {@link #snapshotId()}
     */
    SemanticGraph graph();

    /**
     * Resolves a symbol reference; a foreign snapshot or missing symbol is a typed rejection.
     *
     * @param ref symbol reference
     * @return resolved symbol or a typed rejection
     */
    QueryResult<Symbol> symbol(SymbolRef ref);

    /**
     * Resolves a type reference; a foreign snapshot is a typed rejection.
     *
     * @param ref type reference
     * @return resolved type or a typed rejection
     */
    QueryResult<TypeDescriptor> type(TypeRef ref);

    /**
     * Resolves an expression reference; a foreign snapshot is a typed rejection.
     *
     * @param ref expression reference
     * @return resolved expression or a typed rejection
     */
    QueryResult<TypedExpression> expression(ExpressionRef ref);

    /**
     * Returns the declared type of a symbol, independent of inheritance.
     *
     * @param ref symbol reference
     * @return declared type or a typed rejection
     */
    QueryResult<TypeDescriptor> declaredType(SymbolRef ref);

    /**
     * Returns the effective type after inheritance/override.
     *
     * @param ref symbol reference
     * @return effective type or a typed rejection
     */
    QueryResult<EffectiveType> effectiveType(SymbolRef ref);

    /**
     * Returns effective members in their deterministic INTERLIS order.
     *
     * @param viewable class, structure or association reference
     * @return ordered effective members or a typed rejection
     */
    QueryResult<List<EffectiveMember>> effectiveMembers(SymbolRef viewable);

    /**
     * Resolves a source name reference in its recorded scope.
     *
     * @param reference recorded name reference
     * @return target symbol or a typed rejection
     */
    QueryResult<SymbolRef> resolveReference(ReferenceId reference);

    /**
     * Lists visible symbols under a scope and prefix in deterministic order.
     *
     * @param scope lookup scope
     * @param prefix name prefix
     * @return visible symbols or a typed rejection
     */
    QueryResult<List<SymbolRef>> visibleSymbols(ScopeRef scope, NamePrefix prefix);

    /**
     * Returns members of a known qualifier without climbing its outer scopes.
     * Uses published effective viewable members and topic inheritance, with local
     * declarations before inherited ones. Results are immutable and snapshot-bound.
     *
     * @param owner qualifier declaration in this snapshot
     * @return members, or the same typed unknown/foreign-reference result as symbol
     */
    QueryResult<List<SymbolRef>> membersOf(SymbolRef owner);

    /**
     * Finds symbols by fully qualified name; the result may be empty.
     *
     * @param name canonical qualified name
     * @return matching symbols in canonical order
     */
    List<SymbolRef> findQualifiedName(QualifiedName name);

    /**
     * Rebinds a stable symbol id to the current snapshot, if it exists.
     *
     * @param id stable symbol id
     * @return current-snapshot reference, or empty if absent
     */
    Optional<SymbolRef> rebindSymbol(SymbolId id);

    /**
     * Returns references to a target in canonical source order.
     *
     * @param target symbol in this snapshot
     * @return ordered name references
     */
    List<NameReference> referencesTo(SymbolRef target);

    /**
     * Additional helper used by completion; still deterministic and scoped by prefix.
     *
     * @param prefix name prefix
     * @param kind lookup kind
     * @return symbols in canonical order
     */
    default List<SymbolRef> symbolsWithPrefix(NamePrefix prefix, LookupKind kind) {
        return List.of();
    }
}

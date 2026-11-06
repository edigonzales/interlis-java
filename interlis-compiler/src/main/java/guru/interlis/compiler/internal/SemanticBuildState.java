package guru.interlis.compiler.internal;

import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.NameReference;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The graph being assembled in a single compilation run. */
final class SemanticBuildState implements SemanticGraphRead {
    private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
    private final Map<TypeId, TypeDescriptor> types = new LinkedHashMap<>();
    private final Map<ExpressionId, TypedExpression> expressions = new LinkedHashMap<>();
    private final Map<ReferenceId, NameReference> references = new LinkedHashMap<>();
    private final Map<SymbolId, TypeId> declaredTypes = new LinkedHashMap<>();
    private final Map<SymbolId, EffectiveType> effectiveTypes = new LinkedHashMap<>();
    private final Map<SymbolId, List<EffectiveMember>> effectiveMembers = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> bases = new LinkedHashMap<>();
    private int typeCounter;
    private int expressionCounter;
    private boolean frozen;

    public Symbol symbol(SymbolId id) { return symbols.get(id); }

    boolean hasSymbol(SymbolId id) { return symbols.containsKey(id); }

    void defineSymbol(Symbol symbol) {
        requireOpen();
        if (symbols.putIfAbsent(symbol.id(), symbol) != null) {
            throw new IllegalStateException("Symbol already defined: " + symbol.id());
        }
    }

    void defineSymbolIfAbsent(Symbol symbol) {
        requireOpen();
        symbols.putIfAbsent(symbol.id(), symbol);
    }

    void replaceSymbol(Symbol symbol) {
        requireOpen();
        if (symbols.replace(symbol.id(), symbol) == null) {
            throw new IllegalStateException("Symbol not defined: " + symbol.id());
        }
    }

    /** Read-only live view in insertion order. Take a snapshot before adding symbols while iterating. */
    public Collection<Symbol> symbolsInBuildOrder() { return Collections.unmodifiableCollection(symbols.values()); }

    public TypeDescriptor type(TypeId id) { return types.get(id); }

    void storeType(TypeDescriptor type) {
        requireOpen();
        types.put(type.id(), type);
    }

    public TypedExpression expression(ExpressionId id) { return expressions.get(id); }

    void storeExpression(TypedExpression expression) {
        requireOpen();
        expressions.put(expression.id(), expression);
    }

    void storeReference(NameReference reference) {
        requireOpen();
        references.put(reference.id(), reference);
    }

    public TypeId declaredType(SymbolId id) { return declaredTypes.get(id); }

    boolean hasDeclaredType(SymbolId id) { return declaredTypes.containsKey(id); }


    void bindDeclaredType(SymbolId symbol, TypeId type) {
        requireOpen();
        declaredTypes.put(symbol, type);
    }

    void bindDeclaredTypeIfAbsent(SymbolId symbol, TypeId type) {
        requireOpen();
        declaredTypes.putIfAbsent(symbol, type);
    }

    /** Read-only live view in insertion order. */
    public Set<Map.Entry<SymbolId, TypeId>> declaredTypesInBuildOrder() {
        return Collections.unmodifiableMap(declaredTypes).entrySet();
    }

    void setEffectiveType(SymbolId symbol, EffectiveType type) {
        requireOpen();
        effectiveTypes.put(symbol, type);
    }

    /** Returns the members in effective order, or an empty list if none have been recorded. */
    List<EffectiveMember> effectiveMembers(SymbolId owner) {
        return effectiveMembers.getOrDefault(owner, List.of());
    }

    void setEffectiveMembers(SymbolId owner, List<EffectiveMember> members) {
        requireOpen();
        effectiveMembers.put(owner, List.copyOf(members));
    }

    /** Read-only live view in insertion order, with immutable member lists. */
    Set<Map.Entry<SymbolId, List<EffectiveMember>>> effectiveMembersInBuildOrder() {
        return Collections.unmodifiableMap(effectiveMembers).entrySet();
    }

    public SymbolId baseOf(SymbolId symbol) { return bases.get(symbol); }

    boolean hasBase(SymbolId symbol) { return bases.containsKey(symbol); }

    void bindBase(SymbolId symbol, SymbolId base) {
        requireOpen();
        bases.put(symbol, base);
    }

    TypeId nextTypeId() { return new TypeId("type-" + nextTypeSequence()); }

    int nextTypeSequence() {
        requireOpen();
        return ++typeCounter;
    }

    ExpressionId nextExpressionId() {
        requireOpen();
        return new ExpressionId("expr-" + (++expressionCounter));
    }

    /** Publishes a defensive graph and closes this state to further writes. */
    SemanticGraph freeze(SnapshotId snapshot, List<SymbolId> roots, List<SymbolId> models,
                         Set<LanguageVersion> languages, Map<SourceId, SourceRevision> revisions) {
        requireOpen();
        SemanticGraph graph = new SemanticGraph(snapshot, symbols.values(), types.values(), expressions.values(),
                references.values(), declaredTypes, effectiveTypes, effectiveMembers, roots, models, languages, revisions);
        frozen = true;
        return graph;
    }

    private void requireOpen() {
        if (frozen) throw new IllegalStateException("Semantic graph already frozen");
    }
}

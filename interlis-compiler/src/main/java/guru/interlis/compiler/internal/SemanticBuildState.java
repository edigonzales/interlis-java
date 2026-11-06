package guru.interlis.compiler.internal;

import guru.interlis.model.ConstantExpression;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.FunctionCallExpression;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.LiteralExpression;
import guru.interlis.model.NameReference;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypeKind;
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
final class SemanticBuildState implements SemanticGraphRead.Symbols, SemanticGraphRead.Types,
        SemanticGraphRead.Expressions, SemanticGraphRead.Inheritance {
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

    public boolean hasSymbol(SymbolId id) { return symbols.containsKey(id); }

    public void defineSymbol(Symbol symbol) {
        requireOpen();
        if (symbols.putIfAbsent(symbol.id(), symbol) != null) {
            throw new IllegalStateException("Symbol already defined: " + symbol.id());
        }
    }

    public void replaceSymbol(Symbol symbol) {
        requireOpen();
        Symbol previous = symbols.get(symbol.id());
        if (previous == null) throw new IllegalStateException("Symbol not defined: " + symbol.id());
        boolean attachingPredefined = previous.origin() instanceof Origin.PredefinedOrigin
                && previous.owner().isEmpty() && symbol.owner().isPresent();
        if (previous.kind() != symbol.kind() || !previous.name().equals(symbol.name())
                || !previous.origin().equals(symbol.origin()) || !previous.nameRange().equals(symbol.nameRange())
                || previous.languageVersion() != symbol.languageVersion()
                || (!attachingPredefined && (!previous.owner().equals(symbol.owner())
                        || previous.declarationOrder() != symbol.declarationOrder()))) {
            throw new IllegalStateException("Replacement changes symbol identity: " + symbol.id());
        }
        symbols.replace(symbol.id(), symbol);
    }

    /** Read-only live view in insertion order. Take a snapshot before adding symbols while iterating. */
    public Collection<Symbol> symbolsInBuildOrder() { return Collections.unmodifiableCollection(symbols.values()); }

    public TypeDescriptor type(TypeId id) { return types.get(id); }

    public void defineType(TypeDescriptor type) {
        requireOpen();
        if (types.putIfAbsent(type.id(), type) != null)
            throw new IllegalStateException("Type already defined: " + type.id());
    }

    public void replaceType(TypeDescriptor type) {
        requireOpen();
        TypeDescriptor previous = types.get(type.id());
        if (previous == null) throw new IllegalStateException("Type not defined: " + type.id());
        boolean completion = switch (previous.kind()) {
            case PENDING -> type.kind() != TypeKind.PENDING;
            case NUMERIC, COORDINATE -> type.kind() == previous.kind() || type.kind() == TypeKind.ERROR;
            case ENUMERATION, GEOMETRY -> type.kind() == previous.kind();
            default -> false;
        };
        if (!completion || !previous.origin().equals(type.origin()))
            throw new IllegalStateException("Invalid type completion: " + type.id());
        types.replace(type.id(), type);
    }

    public TypedExpression expression(ExpressionId id) { return expressions.get(id); }

    public void defineExpression(TypedExpression expression) {
        requireOpen();
        if (expressions.putIfAbsent(expression.id(), expression) != null)
            throw new IllegalStateException("Expression already defined: " + expression.id());
    }

    public void replaceExpression(TypedExpression expression) {
        requireOpen();
        TypedExpression previous = expressions.get(expression.id());
        if (previous == null) throw new IllegalStateException("Expression not defined: " + expression.id());
        if (!(previous instanceof LiteralExpression || previous instanceof ConstantExpression
                || previous instanceof FunctionCallExpression) || previous.getClass() != expression.getClass()
                || !previous.range().equals(expression.range()) || !previous.children().equals(expression.children())) {
            throw new IllegalStateException("Replacement changes expression identity: " + expression.id());
        }
        expressions.replace(expression.id(), expression);
    }

    void storeReference(NameReference reference) {
        requireOpen();
        if (references.putIfAbsent(reference.id(), reference) != null)
            throw new IllegalStateException("Reference already defined: " + reference.id());
    }

    public TypeId declaredType(SymbolId id) { return declaredTypes.get(id); }

    public boolean hasDeclaredType(SymbolId id) { return declaredTypes.containsKey(id); }


    public void bindDeclaredType(SymbolId symbol, TypeId type) {
        requireOpen();
        if (declaredTypes.putIfAbsent(symbol, type) != null)
            throw new IllegalStateException("Declared type already bound: " + symbol);
    }

    /** Read-only live view in insertion order. */
    public Set<Map.Entry<SymbolId, TypeId>> declaredTypesInBuildOrder() {
        return Collections.unmodifiableMap(declaredTypes).entrySet();
    }

    public void setEffectiveType(SymbolId symbol, EffectiveType type) {
        requireOpen();
        if (effectiveTypes.putIfAbsent(symbol, type) != null)
            throw new IllegalStateException("Effective type already defined: " + symbol);
    }

    /** Returns the members in effective order, or an empty list if none have been recorded. */
    public List<EffectiveMember> effectiveMembers(SymbolId owner) {
        return effectiveMembers.getOrDefault(owner, List.of());
    }

    public void setEffectiveMembers(SymbolId owner, List<EffectiveMember> members) {
        requireOpen();
        if (effectiveMembers.putIfAbsent(owner, List.copyOf(members)) != null)
            throw new IllegalStateException("Effective members already defined: " + owner);
    }

    /** Read-only live view in insertion order, with immutable member lists. */
    public Set<Map.Entry<SymbolId, List<EffectiveMember>>> effectiveMembersInBuildOrder() {
        return Collections.unmodifiableMap(effectiveMembers).entrySet();
    }

    public SymbolId baseOf(SymbolId symbol) { return bases.get(symbol); }

    public boolean hasBase(SymbolId symbol) { return bases.containsKey(symbol); }

    public void bindBase(SymbolId symbol, SymbolId base) {
        requireOpen();
        SymbolId previous = bases.putIfAbsent(symbol, base);
        if (previous != null && !previous.equals(base))
            throw new IllegalStateException("Conflicting base for symbol: " + symbol);
    }

    public TypeId nextTypeId() { return new TypeId("type-" + nextTypeSequence()); }

    public int nextTypeSequence() {
        requireOpen();
        return ++typeCounter;
    }

    public ExpressionId nextExpressionId() {
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
